import com.walmartlabs.concord.common.Task
import com.walmartlabs.concord.common.InjectVariable

import javax.inject.Named

@Named("myTask")
class MyTask implements Task {

    void hey(@InjectVariable("greeting") String greeting, String name) {
        println String.format(greeting, name)
    }
}