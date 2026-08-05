package com.pmplugin4j.security.servlet;

import jakarta.servlet.Filter;

public interface PreAuthorizeFilterExtension {
    Filter getFilter();

    default int getOrder() {
        return 0;
    }
}
