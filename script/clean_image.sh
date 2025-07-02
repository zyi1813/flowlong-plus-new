DELETE_IMAGES=$(docker image ls --format '{{.ID}} {{.Repository}}:{{.Tag}}' | grep "$docker_host/library/flowlong-plus-server" | grep -v "$docker_tag" | awk '{print $2}')
for TAG_ID in $DELETE_IMAGES; do
    docker image rm $TAG_ID
done