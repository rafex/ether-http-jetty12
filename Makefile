# Set the directory for this project so make deploy need not receive PROJECT_DIR
PROJECT_DIR         := ether-http-jetty12
PROJECT_GROUP_ID    := dev.rafex.ether.http.jetty12
PROJECT_ARTIFACT_ID := ether-http-jetty12

# Include shared build logic
include ../build-helpers/common.mk
include ../build-helpers/git.mk