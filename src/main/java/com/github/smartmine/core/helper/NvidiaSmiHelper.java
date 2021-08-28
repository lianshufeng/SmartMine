package com.github.smartmine.core.helper;

import com.github.smartmine.core.conf.TaskConf;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Component
public class NvidiaSmiHelper implements ApplicationRunner {

    @Autowired
    private TaskConf taskConf;

    private GpuInfo[] gpuInfos;

    //计数器
    private int counter = 0;


    /**
     * 获取Gpu信息
     *
     * @return
     */
    public synchronized GpuInfo getGpuInfo() {
        final GpuInfo gpuInfo = new GpuInfo();
        if (Arrays.stream(this.gpuInfos).filter(it -> it != null).count() == 0) {
            return null;
        }
        Long count = Arrays.stream(this.gpuInfos).filter(it -> it != null).count();


        Arrays.stream(this.gpuInfos).filter(it -> it != null).forEach((it) -> {
            gpuInfo.setGpu(gpuInfo.getGpu() + it.getGpu());
            gpuInfo.setMemory(gpuInfo.getMemory() + it.getMemory());
            gpuInfo.setPower(gpuInfo.getPower() + it.getPower());
            gpuInfo.setTemperature(gpuInfo.getTemperature() + it.getTemperature());
        });


        gpuInfo.setGpu(gpuInfo.getGpu() / count.intValue());
        gpuInfo.setPower(new BigDecimal(gpuInfo.getPower() / count.intValue()).setScale(2, RoundingMode.HALF_UP).doubleValue());
        gpuInfo.setTemperature(gpuInfo.getTemperature() / count.intValue());
        gpuInfo.setMemory(gpuInfo.getMemory() / count.intValue());
        return gpuInfo;
    }


    @Override
    public void run(ApplicationArguments args) throws Exception {
        final List<String> cmds = List.of(
                taskConf.getNvidiaSmiPath().getAbsolutePath(),
                "--format=csv,noheader",
                "--query-gpu=power.draw,temperature.gpu,utilization.gpu,utilization.memory",
                "-l",
                "1"
        );
        ProcessBuilder processBuilder = new ProcessBuilder(cmds);
        final Process process = processBuilder.start();
        @Cleanup InputStream inputStream = process.getInputStream();
        @Cleanup InputStream errorStream = process.getErrorStream();
        readStream(inputStream, errorStream);

        //主线程被销毁则结束监视进程
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            process.destroy();
        }));

        process.waitFor();
        System.out.println("nvidia-smi closed , System exit !!!");
        System.exit(0);
    }


    @Autowired
    private void init(ApplicationContext applicationContext) {
        gpuInfos = new GpuInfo[taskConf.getSampleTime()];
    }

    /**
     * 读取流
     *
     * @param inputStreams
     */
    private void readStream(InputStream... inputStreams) {
        Arrays.stream(inputStreams).forEach((is) -> {
            new Thread(() -> {
                readInputStream(is);
            }).start();
        });
    }

    /**
     * 读取一个流
     *
     * @param inputStream
     */
    @SneakyThrows
    private void readInputStream(InputStream inputStream) {
        byte[] buffer = new byte[10240];
        int size = -1;
        while ((size = inputStream.read(buffer)) != -1) {
            String ret = new String(buffer, 0, size);
            ret = ret.replaceAll("\n", "");
            ret = ret.replaceAll("\r", "");
            record(ret);
        }
    }

    /**
     * 记录数据
     *
     * @param ret
     */
    private synchronized void record(String ret) {
        counter++;
        if (counter < 1) {
            counter = 0;
        }


        String[] items = ret.split(",");
        if (items.length < 3) {
            return;
        }
        int index = counter % this.gpuInfos.length;

        try {
            final GpuInfo gpuInfo = new GpuInfo();
            if (StringUtils.hasText(items[0])) {
                gpuInfo.setPower(Double.parseDouble(getKeyWord(items[0])));
            }
            if (StringUtils.hasText(items[1])) {
                gpuInfo.setTemperature(Integer.parseInt(getKeyWord(items[1])));
            }

            if (StringUtils.hasText(items[2])) {
                gpuInfo.setGpu(Integer.parseInt(getKeyWord(items[2])));
            }

            if (StringUtils.hasText(items[3])) {
                gpuInfo.setMemory(Integer.parseInt(getKeyWord(items[3])));
            }

            this.gpuInfos[index] = gpuInfo;
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    /**
     * 取出关键词
     *
     * @param items
     * @return
     */
    private String getKeyWord(String items) {
        return items.trim().split(" ")[0];
    }


    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GpuInfo {

        //功耗
        private double power;

        //温度
        private int temperature;

        //GPU使用率
        private int gpu;

        //内存使用率
        private int memory;

    }


}
