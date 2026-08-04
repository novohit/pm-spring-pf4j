package ${package};

import com.pmplugin4j.api.PmPlugin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Plugin extends PmPlugin {

    @Override
    protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(
            AnnotationConfigApplicationContext context) {
        return context;
    }

    @Override
    protected void afterApplicationContextReady(AnnotationConfigApplicationContext context) {
    }
}
