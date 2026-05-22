## How to run using kubernetes ??
``` kubectl apply -f k8s.yaml ```
``` kubectl get pods ``` to make sure that all pods are running 
if some servers are not running 
```
cd /home/naduto/Documents/Weather-Stations-Monitoring

# Build each service's image
docker build -t localhost:5000/bitcask-server:latest ./bitcaks/
docker build -t localhost:5000/central-station:latest -f central-station/Dockerfile .
docker build -t localhost:5000/weather-station:latest ./weather-station/

# Push to local registry
docker push localhost:5000/bitcask-server:latest
docker push localhost:5000/central-station:latest
docker push localhost:5000/weather-station:latest

kubectl delete -f k8s.yaml  # Remove old pods
kubectl apply -f k8s.yaml   # Apply updated manifests
```
### Access Kibana web UI
``` kubectl port-forward svc/kibana 5601:5601 ```
#### Then open: http://localhost:5601

#### Port-forward to Elasticsearch
``` kubectl port-forward svc/elasticsearch 9200:9200 ```

#### In another terminal, query the indices:
``` curl http://localhost:9200/_cat/indices ```

#### Get weather data:
``` curl "http://localhost:9200/weather_data/_search?pretty" | head -100 ```

#### Access Kafka inside the pod
```
kubectl exec -it svc/kafka -- bash
```
#### Inside the pod:
```
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic weather_status --from-beginning
```
#### Access bitcask-server pod
```
kubectl exec -it svc/bitcask-server -- bash
```
#### Inside the pod, list stored data:
```
ls -la /app/bitcask-data/
```
#### View bitcask data file
```
cat /app/bitcask-data/segment_1.hint
```
#### to try the bitcask shell first 
```
kubectl port-forward svc/bitcask-server-service 8080:8080
kubectl port-forward svc/kibana-service 5601:5601
cd bitcak
./BitcaskClient.sh

```

#### Check if central-station created Parquet files
```
kubectl exec -it svc/central-station -- bash
```
#### Inside the pod:
```
ls -la /app/parquet-data/

```