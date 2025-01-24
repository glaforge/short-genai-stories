package storygen;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import dev.langchain4j.model.vertexai.VertexAiImageModel;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class ExplicitStoryGeneratorAgent {

    private static final String CHAT_MODEL_NAME = "gemini-2.0-flash-exp";
//    private static final String CHAT_MODEL_NAME = "gemini-1.5-pro-002";
    private static final String IMAGE_MODEL_NAME = "imagen-3.0-generate-001";

    private static final Random RANDOM = new Random();

    private static final Gson GSON = new Gson();

    private static final List<ChatModelListener> CHAT_MODEL_LISTENERS = List.of(new ChatModelListener() {
        @Override
        public void onError(ChatModelErrorContext errorContext) {
            System.out.println("[ON ERROR] " + red(errorContext.error().getMessage()));
        }

        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
            System.out.println("[ON REQUEST] \n" + cyan(requestContext.request().messages().stream()
                .map(chatMessage -> " --> " + chatMessage.toString())
                .collect(Collectors.joining("\n"))));
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
            System.out.println("[ON RESPONSE] ");
            if (responseContext.response().aiMessage().text() != null) {
                System.out.println("  TEXT = " + yellow(responseContext.response().aiMessage().text()));
            }
            if (responseContext.response().aiMessage().hasToolExecutionRequests()) {
                for (ToolExecutionRequest toolExecutionRequest : responseContext.response().aiMessage().toolExecutionRequests()) {
                    System.out.println("  TOOL = " + yellow(toolExecutionRequest.toString()));
                }
            }
        }
    });

    record Story(
        @Description("The title of the story")
        String title,
        @Description("The chapters of the story")
        List<Chapter> chapters) {
        record Chapter(
            @Description("The title of the chapter")
            String chapterTitle,
            @Description("The content of the chapter")
            String chapterContent,
            @Description("The Google Cloud Storage URI of the image that represents the content of the chapter")
            String gcsURI) {
        }
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

        Story story = prepareStory("a science-fiction novel");

        System.out.println(red(story.title) + "\n");
        story.chapters().forEach(chapter -> {
            System.out.println(green(chapter.chapterTitle) + "\n");
            System.out.println(chapter.chapterContent + "\n");
        });

        List<Story.Chapter> newChaptersWithImages = story.chapters.stream().parallel().map(chapter -> {
            System.out.println("Generating images for: " + green(chapter.chapterTitle) + "\n");

            String imagePrompt = prepareImagePromptForChapter(chapter);
            List<String> imagesForChapter = generateImages(imagePrompt);

            String bestImage = pickBestImageForChapter(chapter.chapterContent, imagesForChapter);

            return new Story.Chapter(chapter.chapterTitle, chapter.chapterContent, bestImage);
        }).toList();

        Story newStoryWithImages = new Story(story.title, newChaptersWithImages);

        saveToFirestore(newStoryWithImages);
    }

    private static String pickBestImageForChapter(String chapterContent, List<String> imagesForChapter) {

        record BestImage(
            String bestImage
        ) {}

        var chatModel = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(CHAT_MODEL_NAME)
//            .listeners(CHAT_MODEL_LISTENERS)
            .responseSchema(Schema.newBuilder()
                .setType(Type.OBJECT)
                .putProperties("bestImage", Schema.newBuilder()
                    .setDescription("The Google Cloud Storage URI of the best image for the chapter")
                    .setType(Type.STRING)
                    .build())
                .addAllRequired(List.of("bestImage"))
                .build())
            .build();

        List<ChatMessage> judgementPromptMessages = new ArrayList<>();
        judgementPromptMessages.add(SystemMessage.from("""
                Your role is to judge which image, among several images represented by their GCS URI (Google Cloud Storage URI), \\
                matches the best the given chapter content.
                
                You MUST return JUST the GCS URI of the best image, without any commentary or explanations.
                """));
        judgementPromptMessages.add(UserMessage.from("""
            ### Chapter content
            """ + chapterContent + """
            
            ### Image URLs in Google Cloud Storage
            """));

        for (String urIs : imagesForChapter) {
            judgementPromptMessages.add(UserMessage.from(urIs + "\n"));
            judgementPromptMessages.add(UserMessage.from(ImageContent.from(urIs)));
        }

        Response<AiMessage> response = chatModel.generate(judgementPromptMessages);
        BestImage bestImage = GSON.fromJson(response.content().text(), BestImage.class);

        System.out.println("Best image: " + yellow(bestImage.bestImage));

        return bestImage.bestImage;
    }

    private static List<String> generateImages(String imagePrompt) {

        System.out.println("Image prompt: " + yellow(imagePrompt));

        VertexAiImageModel imageModel = VertexAiImageModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
            .modelName(IMAGE_MODEL_NAME)
            .publisher("google")
            .withPersisting()
            .persistToCloudStorage("gs://genai-java-demos.firebasestorage.app")
            .build();

        Response<List<Image>> imageResponse = imageModel.generate(imagePrompt, 4);

        return imageResponse.content().stream()
            .map(image -> image.url().toString())
            .peek(imageUrl -> System.out.println(green(" - " + imageUrl)))
            .toList();
    }

    private static String prepareImagePromptForChapter(Story.Chapter chapter) {
        record ImagePrompt(
            String imagePrompt
        ) {}

        Response<AiMessage> imagePromptResponse = null;

        try (var chatModel = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(CHAT_MODEL_NAME)
            .temperature(1.5f)
//            .listeners(CHAT_MODEL_LISTENERS)
            .responseSchema(Schema.newBuilder()
                .setType(Type.OBJECT)
                .putProperties("imagePrompt", Schema.newBuilder()
                    .setDescription("An image generation prompt for this chapter")
                    .setType(Type.STRING)
                    .build())
                .addAllRequired(List.of("imagePrompt"))
                .build())
            .build()) {

            imagePromptResponse = chatModel.generate(
                SystemMessage.from("""
                    You are an expert artist who masters crafting great prompts for image generation models, to illustrate short stories.
                    When given a short story, reply with a concise prompt that could be used to create an illustration with the Imagen 3 model.
                    Don't use any flags like those used with MidJourney. Just answer with the short concise text prompt.
                    
                    Your answer MUST start with "A cartoon of ", as we want to use cartoon or comics illustrations.
                    
                    The user gives you the following image prompt for the chapter to illustrate:
                    """),
                UserMessage.from(chapter.chapterContent)
            );
        } catch (IOException e) {
            System.err.println("Exception: " + e.getMessage());
        }

        ImagePrompt imagePrompt = GSON.fromJson(imagePromptResponse.content().text(), ImagePrompt.class);
        return imagePrompt.imagePrompt;
    }

    private static Story prepareStory(String storyType) {
        var chatModel = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(CHAT_MODEL_NAME)
            .temperature(1.5f)
//            .listeners(CHAT_MODEL_LISTENERS)
            .responseSchema(Schema.newBuilder()
                .setType(Type.OBJECT)
                .putProperties("title", Schema.newBuilder()
                    .setDescription("The title of the story")
                    .setType(Type.STRING)
                    .build())
                .putProperties("chapters", Schema.newBuilder()
                    .setDescription("The list of 5 chapters")
                    .setType(Type.ARRAY)
                    .setItems(Schema.newBuilder()
                        .setDescription("A chapter with a title, and its content")
                        .setType(Type.OBJECT)
                        .putProperties("chapterTitle", Schema.newBuilder()
                            .setType(Type.STRING)
                            .setDescription("The title of the chapter")
                            .build())
                        .putProperties("chapterContent", Schema.newBuilder()
                            .setType(Type.STRING)
                            .setDescription("The content of the chapter, made of 20 sentences")
                            .build())
                        .addAllRequired(List.of("chapterTitle", "chapterContent"))
                        .build())
                    .build())
                .addAllRequired(List.of("title", "chapters"))
                .build())
            .build();

        Response<AiMessage> response = chatModel.generate(
            SystemMessage.from("""
                You are a creative fiction author, and your role is to write stories.
                You write a story as requested by the user.

                A story always has a title, and is made of 5 long chapters.
                Each chapter has a title, and is at least 20 sentences long.
                Split the chapter into paragraphs, to improve legibility.
                """),
            UserMessage.from(storyType)
        );

        String responseText = response.content().text();
        Story generatedStory = GSON.fromJson(responseText, Story.class);
        return generatedStory;
    }

    private static void saveToFirestore(Story story) throws IOException, InterruptedException, ExecutionException {
        FirestoreOptions firestoreOptions =
            FirestoreOptions.getDefaultInstance().toBuilder()
                .setProjectId(System.getenv("GCP_PROJECT_ID"))
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build();

        Firestore db = firestoreOptions.getService();

        String title = Normalizer
            .normalize(story.title + "-" + Math.abs(RANDOM.nextInt()), Normalizer.Form.NFD)
            .toLowerCase()
            .replaceAll("\\p{IsM}+", "")
            .replaceAll("\\p{IsP}+", " ")
            .trim()
            .replaceAll("\\s+", "-");

        WriteResult writeResult = db.collection("short-story")
            .document(title)
            .set(Map.of(
                "title", story.title,
                "createdAt", System.currentTimeMillis(),
                "chapters", story.chapters.stream().map(chapter ->
                    Map.of(
                        "chapterTitle", chapter.chapterTitle,
                        "chapterContent", chapter.chapterContent,
                        "image", chapter.gcsURI
                    )).toList()
            )).get();

        System.out.println("Saved in Firestore at: " + writeResult.getUpdateTime());
    }


    private static String red(String msg) {
        return "\u001B[31m\u001B[1m" + msg + "\u001B[22m\u001B[0m";
    }

    private static String green(String msg) {
        return "\u001B[32m\u001B[1m" + msg + "\u001B[22m\u001B[0m";
    }

    private static String blue(String msg) {
        return "\u001B[34m\u001B[1m" + msg + "\u001B[22m\u001B[0m";
    }

    private static String yellow(String msg) {
        return "\u001B[33m\u001B[1m" + msg + "\u001B[22m\u001B[0m";
    }

    private static String cyan(String msg) {
        return "\u001B[36m\u001B[1m" + msg + "\u001B[22m\u001B[0m";
    }

}