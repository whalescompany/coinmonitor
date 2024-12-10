docker build --tag coinmonitor .
docker save coinmonitor | bzip2 | ssh -C makincc docker load