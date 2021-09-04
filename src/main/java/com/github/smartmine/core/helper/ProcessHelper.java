package com.github.smartmine.core.helper;


import com.github.smartmine.core.conf.LimitConf;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.NtDll;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinNT;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.sun.jna.platform.win32.WinNT.PROCESS_QUERY_INFORMATION;
import static com.sun.jna.platform.win32.WinNT.PROCESS_VM_READ;


@Slf4j
@Component
@Scope("prototype")
public class ProcessHelper implements Runnable {

    //进程句柄
    @Setter
    private ProcessHandle processHandle;

    //限制子进程使用率
    @Setter
    private int limitCpuUsage;

    //需要限制的目录
    @Setter
    private File limitPath;


    @Autowired
    private LimitConf limitConf;

    //子进程
    private Map<Long, ProcessHandle> subProcessHandleMap = new ConcurrentHashMap<>();


    final static Kernel32 kernel32 = Kernel32.INSTANCE;
    final static NtDll ntDll = NtDll.INSTANCE;

    //线程池调度器
    ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);


    @Autowired
    private void init(ApplicationContext applicationContext) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executorService.shutdownNow();
        }));

    }


    @Override
    public void run() {
        executorService.scheduleAtFixedRate(() -> {
            scanSubProcess(processHandle);
        }, 3, 3, TimeUnit.SECONDS);

    }

    /**
     * 扫描子进程
     */
    private void scanSubProcess(ProcessHandle parentProcessHandle) {
        parentProcessHandle.children()
                .filter(it -> !subProcessHandleMap.containsKey(it.pid()))
                .forEach((it) -> {
                    log.info("find process : {}", it.info());
                    subProcessHandleMap.put(it.pid(), it);
                    limitProcessCpu(it);
                    //递归寻找子进程
                    scanSubProcess(it);
                });
    }

    @SneakyThrows
    private void limitProcessCpu(ProcessHandle processHandle) {
        File file = getProcessHandleFile(processHandle);
        if (file.getAbsolutePath().indexOf(limitPath.getAbsolutePath()) == -1) {
            return;
        }
        log.info("limit : {} -> {}", file.getAbsolutePath(), limitCpuUsage);
        String[] cmds = new String[]{
                "cmd",
                "/c",
                this.limitConf.getLimitFile().getAbsolutePath(),
                String.valueOf(processHandle.pid()),
                String.valueOf((limitCpuUsage) * 10 * 2),
                String.valueOf((100 - limitCpuUsage) * 10 * 2)
        };
        final Process process = Runtime.getRuntime().exec(cmds);
        new Thread(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();


    }

    private File getProcessHandleFile(ProcessHandle processHandle) {
        //利用jna取出路径
        WinNT.HANDLE _processHandle = kernel32.OpenProcess(PROCESS_VM_READ | PROCESS_QUERY_INFORMATION, true, ((Long) processHandle.pid()).intValue());
        byte[] filename = new byte[1024];
        Psapi.INSTANCE.GetModuleFileNameExA(_processHandle, new WinNT.HANDLE(), filename, filename.length);
        int size = 0;
        for (int i = 0; i < filename.length; i++) {
            if (filename[i] == 0) {
                size = i;
                break;
            }
        }
        return new File(new String(filename, 0, size));
    }

    /**
     * 限制进程的cpu
     */
    private void limitProcessCpu2(ProcessHandle processHandle) {
        System.out.println(processHandle.info());
        //win32 取 进程句柄
        WinNT.HANDLE _processHandle = kernel32.OpenProcess(PROCESS_VM_READ | PROCESS_QUERY_INFORMATION, true, ((Long) processHandle.pid()).intValue());
//        CreateToolhelp32Snapshot
        Long handle = Pointer.nativeValue(_processHandle.getPointer());


//        WinDef.HMODULE hModule = kernel32.GetModuleHandle("ntdll.dll");
//        Function ZwSuspendProcessFunction = Function.getFunction("ntdll.dll", "ZwSuspendProcess");

        Function ZwSuspendProcessFunction = NativeLibrary.getInstance("ntdll").getFunction("ZwSuspendProcess");

        ZwSuspendProcessFunction.invokeVoid(
                new Object[]{
                        new NativeLong(handle.intValue())
                }
        );

//        Object[] args = {new WString(String.valueOf(handle.intValue()))};
//        ZwSuspendProcessFunction.invoke(args);
        log.info("暂停 : {}", processHandle.pid());


//        Pointer pointer = kernel32.GetProcAddress(hModule, );


        // ZwSuspendProcess
        // ZwResumeProcess
        //Function function = Function.getFunction("ntdll.dll", "ZwTerminateProcess");

//        System.out.println(_processHandle);
//        Pointer pointer = kernel32.GetProcAddress(Kernel32.INSTANCE.GetModuleHandle("ZwSuspendProcess"), 1);
//        System.out.println(pointer);
//        Pointer pointer = kernel32.GetProcAddress(
//                Kernel32.INSTANCE.GetModuleHandle("ntdll"), 1
//        );


//        NtSuspendProcess pfnNtSuspendProcess = (NtSuspendProcess)GetProcAddress(
//                GetModuleHandle("ntdll"), "NtSuspendProcess");


//        Function.getFunction(pointer).invoke(new Object[]{handle.intValue()});

//        System.out.println("暂停");
        //Function.getFunction(pointer).invoke(_processHandle.getPointer().getPointerArray());
        //暂停
        //       Function.getFunction(pointer).invoke(new Object[]{
        //               _processHandle
//        });

        //GetProcAddress
    }


}
