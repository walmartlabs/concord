# Kafka Sink

This Kafka-based sink handles process events, process logs, and audit log events.

## Usage

Include this module in the server's [dist](../../dist) module.

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