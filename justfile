set dotenv-load

repoName := "short-genai-stories-repo"
imgName := "short-genai-stories-image"
jobName := "create-new-story"

default:
    echo 'Welcome to the Short GenAI Story Generator!'

serve:
    firebase serve

deploy:
    firebase deploy

[working-directory: 'fictionStoryAgent']
package:
    mvn package

[working-directory: 'fictionStoryAgent']
build:
    gcloud builds submit \
      --region=$GCP_LOCATION \
      --tag $GCP_LOCATION-docker.pkg.dev/$GCP_PROJECT_ID/{{repoName}}/{{imgName}}:latest

create-job:
    gcloud run jobs create {{jobName}} \
      --image $GCP_LOCATION-docker.pkg.dev/$GCP_PROJECT_ID/{{repoName}}/{{imgName}}:latest \
      --region $GCP_LOCATION \
      --set-env-vars "GCP_PROJECT_ID=$GCP_PROJECT_ID" \
      --set-env-vars "GCP_LOCATION=$GCP_LOCATION" \
      --set-env-vars "GCP_VERTEXAI_ENDPOINT=$GCP_LOCATION-aiplatform.googleapis.com:443"

create-scheduler:
    gcloud scheduler jobs create http short-genai-stories-generator-schedule \
      --location $GCP_LOCATION \
      --schedule="0 0 * * *" \
      --uri="https://$GCP_LOCATION-run.googleapis.com/apis/run.googleapis.com/v1/namespaces/$GCP_PROJECT_ID/jobs/{{jobName}}:run" \
      --http-method POST \
      --oauth-service-account-email $GCP_PROJECT_NUM-compute@developer.gserviceaccount.com