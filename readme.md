# Weather Stations Monitoring System

A distributed weather monitoring system built for high-throughput IoT data streams. It uses Kafka to ingest telemetry from simulated edge devices. Features include a custom-built Bitcask key-value store , Parquet file archival , and Elasticsearch/Kibana analytics. Fully containerized with Docker and Kubernetes.

## Table of Contents
 
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Building Docker Images](#building-docker-images)
- [Running with Kubernetes](#running-with-kubernetes)
- [Using the BitCask Client](#using-the-bitcask-client)
- [Accessing Services](#accessing-services)
---
 
## Architecture
 
The system is composed of three stages:
 
```
Data Acquisition          Data Processing & Archiving       Indexing
─────────────────         ───────────────────────────       ────────────────
Weather Stations 1-10  →  Kafka  →  Central Station  →  BitCask (latest state)
Open-Meteo Adapter                       │             →  ElasticSearch / Kibana
                                         ↓                  (historical data)
                                   Parquet Files
```
 
**Components:**
- **Weather Stations (x10)** — mock IoT devices emitting weather readings every second to Kafka
- **Open-Meteo Adapter** — fetches real weather data from the Open-Meteo API and feeds it to Kafka
- **Rain Trigger Processor** — Kafka stream processor that detects humidity > 70% and emits rain alerts
- **Central Station** — consumes Kafka, archives data to Parquet files, updates BitCask store, and indexes to ElasticSearch
- **BitCask Server** — key-value store maintaining the latest reading per station
- **ElasticSearch + Kibana** — historical data indexing and visualization
---
 
## Prerequisites
 
- [Docker](https://docs.docker.com/get-docker/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [Minikube](https://minikube.sigs.k8s.io/docs/start/) or any Kubernetes cluster
- A local Docker registry running on `localhost:5000`
Start a local registry if you don't have one:
```bash
docker run -d -p 5000:5000 --name registry registry:2
```
 
---
 
## Project Structure
 
```
Weather-Stations-Monitoring/
├── bitcasks/                                 # BitCask source code
├── bitcask-test-data/                        # BitCask test data samples
├── central-station/                          # Central Station source code
├── k8s/
├── weather-station/                          # Weather Station source code
├── .gitignore
├── docker-compose.yml
├── pom.xml
├── central-1min.jfr                          # JFR profiling recording (1 min)
├── recording.jfr                             # JFR profiling recording
└── README.md
```
 
---
 
## Building Docker Images
 
Build and push all images to your local registry:
 
```bash
# Weather Station
docker build -t localhost:5000/weather-station:latest ./weather-station
docker push localhost:5000/weather-station:latest
 
# Central Station
docker build -t localhost:5000/central-station:latest ./central-station
docker push localhost:5000/central-station:latest
 
# BitCask Server
docker build -t localhost:5000/bitcask-server:latest ./bitcask-server
docker push localhost:5000/bitcask-server:latest
```
 
> If using Minikube, point your shell to Minikube's Docker daemon first:
> ```bash
> eval $(minikube docker-env)
> ```
 
---
 
## Running with Kubernetes
 
Apply all manifests in dependency order:
 
```bash
# 1. Storage (must be first)
kubectl apply -f k8s/storage/
 
# 2. Kafka (stations and central station depend on it)
kubectl apply -f k8s/kafka/
 
# 3. ElasticSearch & Kibana
kubectl apply -f k8s/elasticsearch/
 
# 4. BitCask Server
kubectl apply -f k8s/bitcask-server/
 
# 5. Central Station
kubectl apply -f k8s/central-station/
 
# 6. Weather Stations & Open-Meteo Adapter
kubectl apply -f k8s/weather-stations/
```
 
Or apply everything at once (order not guaranteed):
```bash
kubectl apply -f k8s/ -R
```
 
**Verify everything is running:**
```bash
kubectl get pods
kubectl get services
```
 
**View logs for a specific component:**
```bash
kubectl logs deployment/central-station
kubectl logs deployment/weather-station-1
kubectl logs deployment/kafka
```
 
**Tear down:**
```bash
kubectl delete -f k8s/ -R
```
 
---
 
## Using the BitCask Client
 
The BitCask client script lets you inspect the key-value store.
 
**View all keys and their latest values** (outputs a timestamped CSV file):
```bash
./bitcask_client.sh --view-all
# Output: 1746034451.csv  (columns: key, value)
```
 
**View a specific key:**
```bash
./bitcask_client.sh --view --key=SOME_KEY
# Prints the value to stdout
```
 
**Performance test with concurrent clients:**
```bash
./bitcask_client.sh --perf --clients=100
# Starts 100 threads each querying all keys
# Output: 1746034451_thread_1.csv, 1746034451_thread_2.csv, ...
```
 
---
 
## Accessing Services
 
| Service       | URL                          | Notes                        |
|---------------|------------------------------|------------------------------|
| Kibana        | http://localhost:30601        | Dashboard & visualizations   |
| BitCask Server| http://localhost:30080        | REST API for key-value store |
| ElasticSearch | http://localhost:9200         | Direct ES queries            |
| Kafka         | kafka-service:9092            | Internal cluster access only |
 
> If using Minikube, replace `localhost` with the Minikube IP:
> ```bash
> minikube ip
> ```