package com.project.fnb.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forward all non-API, non-static paths to index.html
 * so React Router can handle client-side routing.
 * 
 * This prevents 404/401 errors when users refresh the page
 * or navigate directly to a route like /dashboard, /pos, etc.
 */
@Controller
public class SPAForwardingController {

    @RequestMapping(value = {
        "/{path:[^.]*}",            // Match paths without dots (e.g., /dashboard, /pos)
        "/{path:^(?!api|actuator|ws|assets).*$}/**"  // Match paths that don't start with api, actuator, ws, assets
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
