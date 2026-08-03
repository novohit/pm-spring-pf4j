/*
 * Copyright (c) 2025 grejeff.
 */

package com.pmplugin4j;

/**
 * Seven insertion positions in the Spring Security filter chain. Plugins declare filters at one of these positions; the
 * host decides (via configuration) which positions are enabled.
 * <p>
 * {@code AUTHENTICATION} is occupied by the framework's {@code IPluginAuthenticationProvider} chain — plugins do not
 * implement a filter interface at this position.
 */
public enum PmPluginFilterPosition {
    FIRST,
    SESSION_RESTORE,
    FORM_LOGIN,
    AUTHENTICATION,
    ANONYMOUS,
    PRE_AUTHORIZE,
    LAST
}
