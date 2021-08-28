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
            //创建进程,减去波动值
            if (canUsedGpu > 0) {
                startTask(canUsedGpu - this.taskConf.getWaveGpu());
            }
            return;
        }


        //一定的周期内，不调整
        if (currentTask.getCreateTime() + taskConf.getMineIntervalTime() * 1000 > System.currentTimeMillis()) {
            log.debug("周期内无需调整 : {}", System.currentTimeMillis() - currentTask.getCreateTime());
            return;
        }


        //更新条件
        if (canUsedGpu > this.taskConf.getMinIncomeGpu()) {
            log.info("满足收益要求，准备提高: {}", canUsedGpu);
            BehashHelper.this.destroy();
            return;
        }


        //Gpu不够用则结束任务，等待下次创建任务
        if (canUsedGpu < 0) {
            log.info("可用GPU不够，调整中 : {}", canUsedGpu);
            BehashHelper.this.destroy();
        }

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
        iniFile.put("config", "minwindow", "1");
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
