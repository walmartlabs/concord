import com.walmartlabs.concord.runtime.v2.sdk.Task
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult
import com.walmartlabs.concord.runtime.v2.sdk.Variables

import javax.inject.Named

@Named("myTask")
class MyTask implements Task {

    TaskResult execute(Variables input) {
        println "Hey, ${input.get('name')}"
        return TaskResult.success()
    }
}