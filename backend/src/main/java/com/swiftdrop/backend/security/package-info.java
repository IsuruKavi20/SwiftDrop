/**
 * Token checks for the whole API. An OPEN module: every module may use all of it. It must not depend on any business module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Security", type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.swiftdrop.backend.security;
