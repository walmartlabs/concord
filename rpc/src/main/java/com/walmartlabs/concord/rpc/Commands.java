package com.walmartlabs.concord.rpc;

import java.util.HashMap;
import java.util.Map;

public final class Commands {

    public static final String TYPE_KEY = "type";
    public static final String INSTANCE_ID_KEY = "instanceId";

    public static Map<String, Object> toMap(Command cmd) {
        Map<String, Object> m = new HashMap<>();
        m.put(TYPE_KEY, cmd.getType().toString());

        if (cmd instanceof CancelJobCommand) {
            m.put(INSTANCE_ID_KEY, ((CancelJobCommand) cmd).getInstanceId());
        }

        return m;
    }

    public static Command fromMap(Map<String, Object> m) {
        CommandType t = CommandType.valueOf((String) m.get(TYPE_KEY));

        switch (t) {
            case CANCEL_JOB: {
                String instanceId = (String) m.get(INSTANCE_ID_KEY);
                return new CancelJobCommand(instanceId);
            }
            default:
                throw new IllegalArgumentException("Unknown command type: " + t);
        }
    }

    private Commands() {
    }
}
