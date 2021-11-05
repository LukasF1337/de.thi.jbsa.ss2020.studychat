#! /bin/bash
# Format disk if it was not formatted before
if [[ ! $(sudo blkid $(sudo readlink -f /dev/disk/by-id/google-studychat-data)) ]] ; then
        sudo mkfs.ext4 -m 0 -E lazy_itable_init=0,lazy_journal_init=0,discard /dev/disk/by-id/google-studychat-data
fi
# Mount disk
sudo mkdir -p /studychat
sudo mount -o discard,defaults /dev/disk/by-id/google-studychat-data /studychat
# Install docker
if ! [ -x "$(command -v docker)" ] ; then
    sudo curl https://get.docker.com/ -o get_docker
    sudo sh get_docker
fi
# Install docker-compose with pip3. Installing with apt-get produces a bug pulling images
if ! [ -x "$(command -v docker-compose)" ] ; then
    sudo apt update
    sudo apt -y install python3-venv python3-pip
    sudo pip3 install docker-compose
fi
# Move previous transmitted docker-compose.yml to correct directory
sudo mv /tmp/docker-compose.yml /studychat/docker-compose.yml
cd /studychat
# Start services
sudo docker-compose up -d