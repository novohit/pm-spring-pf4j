package ${package};

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plugin")
public class PluginController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello from ${artifactId}";
    }
}
