# This script cuts and uploads a release JAR to
# our google cloud storage.
#
# https://storage.googleapis.com/sourcegraph-for-bitbucket-server/latest.jar
#
# The dependencies are:
# 1) atlassian sdk (for building jar)
# 2) gcloud cli setup with sourcegraph-dev project (for uploading jar)
#
# This script is to be run in the root folder.
#
# ./scripts/release.sh <OLD VERSION> <NEW VERSION>
#
# ex: ./scripts/release.sh 1.1.0 1.2.0

rm -rf ./target/
atlas-package
gsutil mv gs://sourcegraph-for-bitbucket-server/latest.jar gs://sourcegraph-for-bitbucket-server/${1}.jar
gsutil cp ./target/sourcegraph-bitbucket-${2}.jar gs://sourcegraph-for-bitbucket-server/latest.jar

# Allow anyone to download
gsutil acl ch -u AllUsers:R gs://sourcegraph-for-bitbucket-server/${1}.jar
gsutil acl ch -u AllUsers:R gs://sourcegraph-for-bitbucket-server/latest.jar
