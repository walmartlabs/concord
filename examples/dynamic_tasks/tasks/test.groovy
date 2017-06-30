import com.walmartlabs.concord.common.Task

import javax.inject.Named

@Named("myTask")
class MyTask implements Task {

    void hey(String name) {
        println "Hey, ${name}"
    }
}