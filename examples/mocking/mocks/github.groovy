import com.walmartlabs.concord.sdk.*

import javax.inject.Named

@Named("github")
class MyTask implements Task {

    void execute(Context ctx) throws Exception {
        throw new RuntimeException("Kablamo!")
    }
}