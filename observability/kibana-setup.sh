#!/bin/bash
# Wait for Kibana to be ready
until curl -s http://localhost:5601/api/status | grep -q '"overall":{"level":"available"'; do
  echo "Waiting for Kibana to be ready..."
  sleep 5
done

echo "Kibana is ready. Creating data views..."

# Create data views for the OTel indices
curl -X POST "http://localhost:5601/api/data_views/data_view" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{"data_view":{"title":"drinksync-logs*","name":"DrinkSync Logs","timeFieldName":"@timestamp"}}'

curl -X POST "http://localhost:5601/api/data_views/data_view" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{"data_view":{"title":"drinksync-traces*","name":"DrinkSync Traces","timeFieldName":"@timestamp"}}'

curl -X POST "http://localhost:5601/api/data_views/data_view" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{"data_view":{"title":"drinksync-metrics*","name":"DrinkSync Metrics","timeFieldName":"@timestamp"}}'

echo ""
echo "Kibana data views created!"
