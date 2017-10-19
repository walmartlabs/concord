package com.walmartlabs.concord.agent;

public final class Utils {

    public static boolean kill(Process proc) {
        if (!proc.isAlive()) {
            return false;
        }

        proc.destroy();

        if (proc.isAlive()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        while (proc.isAlive()) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                // ignore
            }
            proc.destroyForcibly();
        }

        return true;
    }

    private Utils() {
    }
}
