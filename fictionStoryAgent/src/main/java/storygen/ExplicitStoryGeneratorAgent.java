/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package storygen;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import com.google.gson.Gson;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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
import java.util.function.Supplier;

public class ExplicitStoryGeneratorAgent {

//    private static final String CHAT_MODEL_NAME = "gemini-2.0-flash-exp";
//    private static final String CHAT_MODEL_NAME = "gemini-1.5-flash-002";
    private static final String CHAT_MODEL_NAME = "gemini-1.5-pro-002";
    private static final String IMAGE_MODEL_NAME = "imagen-3.0-generate-001";

    private static final String GCP_PROJECT_ID = System.getenv("GCP_PROJECT_ID");
    private static final String GCP_LOCATION = System.getenv("GCP_LOCATION");
    private static final String GCP_VERTEXAI_ENDPOINT = System.getenv("GCP_VERTEXAI_ENDPOINT");

    private static final Supplier<VertexAiGeminiChatModel.VertexAiGeminiChatModelBuilder> CHAT_MODEL_BUILDER = () ->
        VertexAiGeminiChatModel.builder()
            .project(GCP_PROJECT_ID)
            .location(GCP_LOCATION)
            .modelName(CHAT_MODEL_NAME);

    private static final Random RANDOM = new Random();

    private static final Gson GSON = new Gson();

    /**
     * Different types and arcs of science-fiction.
     * Labels coming form:
     * https://www.rachelagreco.com/30-types-of-science-fiction-every-sci-fi-lover-should-know/
     */
    enum StoryType {
        ALIEN_INVASION("""
            Alien Invasion: An often technologically-superior extraterrestrial society \
            invades Earth with an evil intent, whether to enslave humans, eat them, or \
            use the planet for some other destructive purpose."""),
        ALTERNATE_HISTORY("""
            Alternate History: This sub-genre asks the question: What if history had \
            occurred differently? This type of fiction consists of a change that causes \
            history to diverge from the history we know.
            """),
        ALTERNATE_OR_PARALLEL_UNIVERSE("""
            Alternate/Parallel Universe: This type of fiction has a self-contained \
            separate reality coexisting with our own and can vary from a small \
            geographic region to an entire universe.
            """),
        APOCALYPTIC_AND_POSTAPOCALYPTIC("""
            Apocalyptic/Post-Apocalyptic: Apocalyptic stories are set in a world that \
            touches on the end of civilization, through nuclear war, plague, or some \
            other disaster. Post-Apocalyptic is set in a place where such a disaster \
            has already occurred, where the survivors have to deal with the aftermath, \
            whether days after the disaster or years.
            """),
        ARTIFICIAL_INTELLIGENCE("""
            Artificial Intelligence: Any story that has Artificial Intelligence (AI) as \
            the main theme. AI is a branch of computer science that consists of \
            intelligent behavior, learning and adaptation in machines.
            """),
        COLONIZATION("""
            Colonization: Life forms (usually humans or insects) move into a distant \
            area where their kind is sparse or not yet existing and set up new \
            settlements in the area.
            """),
        CYBERPUNK("""
            Cyberpunk: This name comes from cybernetics (replacing parts of your body \
            with machinery, like a cyborg) and punk. It features advanced \
            technology–computers or information technology–coupled with some degree of \
            breakdown in the social order. The main characters are often marginalized, \
            alienated loners who live on the edge of society.
            """),
        DYING_EARTH("""
            Dying Earth: This sub-genre is similar to apocalyptic/post-apocalyptic in \
            that the end of the world is coming. But unlike those, the end of the world \
            is just due to the natural laws of the universe, in which the sun is fading \
            and the earth’s dying (poor thing!).
            """),
        DYSTOPIA("""
            Dystopia: Some people argue that this is a genre all on its own under the \
            Speculative Fiction umbrella. Regardless, many sci-fi books fall into this \
            category, where the world is opposite of a utopia: more of a nightmare. \
            Oftentimes, the world is shown to be good/perfect/utopian, but the reader \
            soon finds out it isn’t.
            """),
        FIRST_CONTACT("""
            First Contact: Features the first meeting between humans and aliens (or any \
            two sentient races). Unlike Alien Invasion, the aliens in this subgenre \
            aren’t necessarily hostile.
            """),
        GALACTIC_EMPIRE("""
            Galactic Empire: Any books with a Galactic Empire like that in Star Wars.
            """),
        GENERATION_SHIP("""
            Generation Ship: A subgenre in which characters travel on a type of \
            starship called a generation ship much slower than light between stars. \
            Because they’re traveling so slow and it could take thousands of years to \
            reach their destination, the original occupants would die during the \
            journey, leaving their descendants to continue traveling
            """),
        HUMAN_DEVELOPMENT("""
            Human Development: Books in which science or nature has given humans \
            enhanced mental or physical abilities.
            """),
        IMMORTALITY("""
            Immortality: Stories that explore what form an unending or \
            indefinitely-long human life would take, or whether it’s even possible.
            """),
        LIGHT_AND_HUMOROUS("""
            Light/Humorous SF: Another broader subgenre that encompasses \
            any sci-fi books containing humorous sci-fi.
            """),
        MILITARY("""
            Military: War, as the solution to either interstellar or interplanetary \
            conflict, makes up the main or partial plot of these stories. The main \
            characters are often part of the military.
            """),
        MUNDANE("""
            Mundane: Characters remain on earth in this subgenre, and there’s a \
            believability of the use of science and technology at the time the \
            book’s written.
            """),
        MUTANTS("""
            Mutants: Characters who exhibit powers often like superheroes. Unlike \
            the human development subgenre, these powers come about more naturally, \
            as opposed to via experiments. Think zombies more than Teenage Mutant \
            Ninja Turtles.
            """),
        NANOTECHNOLOGIY("""
            Nanotechnology: Books that focus on this kind of science, which is \
            about designing and producing devices and/or systems on the nanoscale
            """),
        NEAR_FUTURE("""
            Near-Future: These stories take place in the present day or in the \
            next few decades, and the setting should be somewhat familiar to the \
            reader. The technology is often current or in development.
            """),
        ROBOTS_AND_ANDROIDS("""
            Robots and/or Androids: Stories that have robots and/or androids.
            """),
        SCIENCE_FANTASY("""
            Science-Fantasy: This subgenre represents works that use main \
            elements of both genres to create a story that is futuristic and \
            technical with fantastical subplots and characters. Or a book that \
            contains, according to Arthur C. Clarke, “any sufficiently advanced \
            technology [that] is indistinguishable from magic.”
            """),
        SPACE_EXPLORATION("""
            Space Exploration: Any story that touches upon the act of \
            exploring space, including all the politics, science, and \
            engineering behind space flight.
            """),
        SPACE_OPERA("""
            Space Opera: Space operas are usually set in outer space or on a \
            far-flung planet. These are adventurous books that emphasize space \
            warfare, drama, interplanetary battles, chivalric romance, and \
            large stakes. It’s often soft science, but not necessarily.
            """),
        STREAMPUNK("""
            Steampunk: Very similar to the subgenre in fantasy, where the focus \
            is on steam-powered technology. However, unlike in the fantasy \
            subgenre, the focus is more on the science and how the technology works.
            """),
        TERRAFORMING("""
            Terraforming: A story about modifying a planet or moon so it’s more habitable.
            """),
        TIME_TRAVEL("""
            Time Travel: Moving between different times or universes.
            """),
        UPLIFT("""
            Uplift: When an advanced civilization helps the development of \
            another one, by giving a non-sentient species sentience, \
            spacefaring capabilities, or some other help.
            """),
        UTOPIA("""
            Utopia: Unlike dystopia, in which the world is supposed to be \
            perfect but has gone awry, in utopia books the world is still perfect.
            """),
        VIRTUAL_REALITY("""
            Virtual Reality: Books in which virtual reality (a technology which \
            allows a person to interact with a computer-simulated environment) \
            plays an integral part of the plot and/or setting.
            """);

        private String explanation;

        StoryType(String explanation) {
            this.explanation = explanation;
        }

        private static final StoryType[] STORY_TYPES = values();
        private static final int SIZE = STORY_TYPES.length;

        public static StoryType randomStoryType()  {
            return STORY_TYPES[RANDOM.nextInt(SIZE)];
        }
    }

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

        StoryType storyType = StoryType.randomStoryType();
        System.out.println("Story type: " + yellow(storyType.name()));
        Story story = prepareStory(storyType.explanation);

        System.out.println(blue(story.title) + "\n");
        story.chapters().forEach(chapter -> {
            System.out.println(green(chapter.chapterTitle) + "\n");
            System.out.println(chapter.chapterContent + "\n");
        });

        List<Story.Chapter> newChaptersWithImages = story.chapters.stream().parallel().map(chapter -> {
            System.out.println("Generating images for: " + green(chapter.chapterTitle) + "\n");

            String imagePrompt = prepareImagePromptForChapter(chapter);
            System.out.println("Image prompt: " + yellow(imagePrompt));

            List<String> imagesForChapter = generateImages(imagePrompt);
            imagesForChapter.forEach(imageUrl -> System.out.println(green(" - " + imageUrl)));

            String bestImage = pickBestImageForChapter(chapter.chapterContent, imagesForChapter);
            System.out.println("Best image: " + yellow(bestImage));

            String moreLegibleChapter = improveChapterLegibility(chapter.chapterContent);
            System.out.println("Update chapter's content: " + green(chapter.chapterTitle) + "\n\n" + moreLegibleChapter);

            return new Story.Chapter(chapter.chapterTitle, moreLegibleChapter, bestImage);
        }).toList();

        Story newStoryWithImages = new Story(story.title, newChaptersWithImages);

        Timestamp timestamp = saveToFirestore(newStoryWithImages);
        System.out.println("Saved in Firestore at: " + timestamp);
    }

    private static Story prepareStory(String storyType) {
        var chatModel = CHAT_MODEL_BUILDER.get()
            .temperature(1.5f)
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
                Each chapter has a title, is split into paragraphs, \
                and is at least 20 sentences long.
                """),
            UserMessage.from(storyType)
        );

        String responseText = response.content().text();
        Story generatedStory = GSON.fromJson(responseText, Story.class);
        return generatedStory;
    }

    private static String prepareImagePromptForChapter(Story.Chapter chapter) {
        record ImagePrompt(
            String imagePrompt
        ) {}

        Response<AiMessage> imagePromptResponse = null;

        try (var chatModel = CHAT_MODEL_BUILDER.get()
            .temperature(1.5f)
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

    private static List<String> generateImages(String imagePrompt) {
        VertexAiImageModel imageModel = VertexAiImageModel.builder()
            .project(GCP_PROJECT_ID)
            .location(GCP_LOCATION)
            .endpoint(GCP_VERTEXAI_ENDPOINT)
            .modelName(IMAGE_MODEL_NAME)
            .publisher("google")
            .withPersisting()
            .persistToCloudStorage("gs://genai-java-demos.firebasestorage.app")
            .build();

        Response<List<Image>> imageResponse = null;
        try {
            imageResponse = imageModel.generate(imagePrompt, 4);
        } catch (Exception e) {
            System.out.println(red(e.getMessage()) + ", regenerating images...");
            imageResponse = imageModel.generate(imagePrompt + "\nDon't generate images with children, only adults.", 4);
        }

        return imageResponse.content().stream()
            .map(image -> image.url().toString())
            .toList();
    }

    private static String pickBestImageForChapter(String chapterContent, List<String> imagesForChapter) {
        record BestImage(
            String bestImage
        ) {}

        var chatModel = CHAT_MODEL_BUILDER.get()
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

        return bestImage.bestImage;
    }

    private static String improveChapterLegibility(String chapterContent) {
        var chatModel = CHAT_MODEL_BUILDER.get()
            .temperature(0.5f)
            .build();

        return chatModel.generate(
            "Split the following text into different paragraphs, to improve legibility:\n\n" +
                chapterContent);
    }

    private static Timestamp saveToFirestore(Story story) throws IOException, InterruptedException, ExecutionException {
        FirestoreOptions firestoreOptions =
            FirestoreOptions.getDefaultInstance().toBuilder()
                .setProjectId(GCP_PROJECT_ID)
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

        return writeResult.getUpdateTime();
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