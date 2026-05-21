{{- define "tripplanning.waitForRedis" -}}
- name: wait-for-redis
  image: busybox:1.36
  command:
    - sh
    - -c
    - until nc -z redis 6379; do echo "waiting for redis..."; sleep 2; done
{{- end -}}

{{- define "tripplanning.waitForElasticsearch" -}}
- name: wait-for-elasticsearch
  image: busybox:1.36
  command:
    - sh
    - -c
    - until wget -qO- http://elasticsearch:9200/_cluster/health?wait_for_status=yellow\&timeout=1s >/dev/null 2>&1; do echo "waiting for elasticsearch..."; sleep 3; done
{{- end -}}
