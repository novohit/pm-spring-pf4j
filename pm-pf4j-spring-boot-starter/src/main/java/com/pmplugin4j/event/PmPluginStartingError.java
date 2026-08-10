package com.pmplugin4j.event;

import java.io.Serial;
import java.io.Serializable;

public class PmPluginStartingError implements Serializable {

    @Serial
    private static final long serialVersionUID = -153864270345999338L;

    private final String pluginId;

    private final String errorMessage;

    private final String errorDetail;

    public String getPluginId() {
        return pluginId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public PmPluginStartingError(String pluginId, String errorMessage, String errorDetail) {
        this.pluginId = pluginId;
        this.errorMessage = errorMessage;
        this.errorDetail = errorDetail;
    }

    public PmPluginStartingError(String pluginId, Throwable e) {
        this.pluginId = pluginId;
        this.errorMessage = e.getMessage();
        this.errorDetail = buildCauseChain(e);
    }

    private static String buildCauseChain(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            sb.append(current.toString());
            current = current.getCause();
            if (current != null) {
                sb.append("\nCaused by: ");
            }
        }
        return sb.toString();
    }
}
