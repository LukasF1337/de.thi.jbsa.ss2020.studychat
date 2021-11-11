variable "project" {
  type = string
}

variable "region" {
  type = string
}

variable "zone" {
  type = string
}

provider "google" {
  project = var.project
  region  = var.region
  zone    = var.zone
}

# Remove this block if you try to apply this config for your own project
terraform {
  backend "gcs" {
    bucket  = "tf-state-studychat"
    prefix  = "terraform-studychat-state"
  }
}

# External disk for persisting data. Used for the databases
resource "google_compute_disk" "studychat-disk" {
  name = "studychat-data"
  zone =  var.zone
  type = "pd-standard"
  size = 5
}

# Allow ssh and http access
resource "google_compute_firewall" "studychat-firewall" {
  name = "studychat-firewall"
  network = "default"
  allow {
    protocol = "tcp"
    ports = ["22", "80"]
  }
  target_tags = ["studychat"]
  source_ranges = ["0.0.0.0/0"]
}

# External ip address to access the instance from the internet
resource "google_compute_address" "static-ip-address" {
  name = "studychat-address"
}

# The vm instance itself
resource "google_compute_instance" "studychat" {
  name         = "studychat"
  machine_type = "e2-medium"
  zone         = var.zone

  tags         = ["studychat", "http-server"]

  boot_disk {
    auto_delete = true
    initialize_params {
      image = "debian-cloud/debian-11"
      size = 10
    }
  }

  attached_disk {
      source = "studychat-data"
      device_name = "studychat-data"
  }

  network_interface {
    network = "default"

    access_config {
      nat_ip = google_compute_address.static-ip-address.address
    }
  }

  lifecycle {
    ignore_changes = [attached_disk]
  }

  # Pass startup script to the instance
  metadata_startup_script = file("${path.module}/startup.sh")

  # Transmit the docker-compose.yml file to the newly created instance
  provisioner "local-exec" {
    command  = "gcloud compute scp --project ${var.project} --zone ${var.zone} --force-key-file-overwrite --ssh-key-expire-after=10.0S docker-compose.yml studychat:/tmp/docker-compose.yml"
  }

}
