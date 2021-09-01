package com.github.smartmine.core.helper;

import com.github.smartmine.core.conf.BehashConf;
import com.github.smartmine.core.conf.TaskConf;
import com.github.smartmine.core.service.TaskService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.ini4j.Ini;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Optional;

@Slf4j
@Component
public class BehashHelper {


    //当前任务
    @Getter
    private TaskItem currentTask;

    @Autowired
    private BehashConf behashConf;

    @Autowired
    private TaskConf taskConf;

    @Autowired
    private NvidiaSmiHelper nvidiaSmiHelper;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskProgressHelper taskProgressHelper;

    //结束任务的时间
    private long endTaskTime = 0;


    @Autowired
    private void init(ApplicationContext applicationContext) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            BehashHelper.this.destroy();
        }));
    }


    /**
     * 更新或创建
     */
    @SneakyThrows
    public synchronized void update(int canUsedGpu) {

        //创建新任务
        if (currentTask == null) {

            //刚结束的进程不启动新任务
            if (this.endTaskTime + taskConf.getKeepTaskStatTime() * 1000 > System.currentTimeMillis()) {
                return;
            }

            //空余GPU-收益率/2
            int taskGpu = canUsedGpu - (int) (this.taskConf.getMinIncomeGpu() / 3 * 2);
            if (taskGpu > 0) {
                startTask(taskGpu);
            }
            return;
        }


        //周期内，不调整
        if (currentTask.getCreateTime() + taskConf.getKeepTaskStatTime() * 1000 > System.currentTimeMillis()) {
            return;
        }


        //收益的增减或者正常状态
        this.taskProgressHelper.updateState(canUsedGpu, () -> {
            log.info("收益调整 : {}", canUsedGpu);
            BehashHelper.this.destroy();
        });


    }


    @SneakyThrows
    private void startTask(int usedGpu) {
        //修改配置文件
        File configFile = new File(this.behashConf.getHomePath().getAbsolutePath() + "/config/behash.ini");
        Ini iniFile = new Ini();
        iniFile.load(configFile);
        iniFile.put("config", "uid", this.behashConf.getUid().trim());
        //自动更新
        iniFile.put("config", "autoupdate", "0");
        //状态栏
        iniFile.put("config", "icon", "1");
        //最小化窗口
        iniFile.put("config", "minwindow", this.behashConf.isShow() ? "0" : "1");
        //GPU
        iniFile.put("card", "power", String.valueOf(usedGpu));
        //自动开始
        iniFile.put("card", "autostart", "1");


        iniFile.store(configFile);

        File behashFile = new File(this.behashConf.getHomePath().getAbsolutePath() + "/behash.exe");
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(behashFile.getAbsolutePath());
        final Process process = processBuilder.start();
        final ProcessHandle processHandle = process.toHandle();
        this.currentTask = TaskItem
                .builder()
                .processHandle(processHandle)
                .createTime(System.currentTimeMillis())
                .gpu(usedGpu)
                .build();

        new Thread(() -> {
            try {
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();
    }

    public void destroy() {
        if (currentTask == null) {
            return;
        }
        Optional.ofNullable(currentTask.getProcessHandle()).ifPresent((processHandle) -> {
            log.info("killTask -> " + processHandle);
            killProcess(processHandle);
            processHandle.destroy();
        });

        currentTask = null;
        endTaskTime = System.currentTimeMillis();
    }


    private void killProcess(ProcessHandle process) {
        if (process.children().count() == 0) {
            process.destroy();
        } else {
            process.children().forEach((it) -> {
                killProcess(it);
            });
        }
    }


    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaskItem {

        //任务进程
        private ProcessHandle processHandle = null;

        //创建时间
        private long createTime = 0;

        //使用GPU
        private int gpu;


    }

}
