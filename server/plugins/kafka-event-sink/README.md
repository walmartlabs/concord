# Kafka Sink

A Kafka-based sink for process events, process logs and audit log events.

## Usage

Must be included into the server's [dist](../../dist) module.

## Configuration

```
# concord-server.conf
concord-server {
    eventSink {
        kafka {
            enabled = true
            bootstrapServers = "localhost:9092"
            processEventsTopic = "process_events"
            processLogsTopic = "process_logs"
            auditLogTopic = "audit_log"
        }
    }
}
```