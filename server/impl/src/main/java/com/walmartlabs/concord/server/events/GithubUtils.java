package com.walmartlabs.concord.server.events;

public final class GithubUtils {

    public static String getRepositoryName(String repoUrl) {
        String repoPath = getRepoPath(repoUrl);

        String[] u = repoPath.split("/");
        if(u.length < 2) {
            return null;
        }
        return owner(u[0]) + "/" + name(u[1]);
    }

    private static String getRepoPath(String repoUrl) {
        String u = removeSchema(repoUrl);
        u = removeHost(u);
        return u;
    }

    private static String removeSchema(String repoUrl) {
        int index = repoUrl.indexOf("://");
        if (index > 0) {
            return repoUrl.substring(index + "://".length());
        }
        index = repoUrl.indexOf("@");
        if (index > 0) {
            return repoUrl.substring(index + "@".length());
        }
        return repoUrl;
    }

    private static String removeHost(String repoUrl) {
        int index = repoUrl.indexOf(":");
        if (index > 0) {
            int portEndIndex = repoUrl.indexOf("/", index);
            if(portEndIndex > 0) {
                String port = repoUrl.substring(index + 1, portEndIndex);
                if (isPort(port)) {
                    return repoUrl.substring(portEndIndex + "/".length());
                }
            }
            return repoUrl.substring(index + ":".length());
        }
        index = repoUrl.indexOf("/");
        if (index > 0) {
            return repoUrl.substring(index + "/".length());
        }
        return repoUrl;
    }

    private static String name(String str) {
        return str.replaceAll("^\\W+|\\.git$", "");
    }

    private static String owner(String str) {
        int idx = str.indexOf(':');
        if (idx > 0) {
            return str.substring(idx + 1);
        }
        return str;
    }

    private static boolean isPort(String str) {
        try {
            int port = Integer.parseInt(str);
            return port > 0 && port <= 65535;
        } catch(NumberFormatException e) {
            return false;
        }
    }

    private GithubUtils() {
    }
}
