package org.benjamin.jpk;

import com.component.alarmAgent.common.gatherData.SigarHunter;
import lombok.extern.slf4j.Slf4j;
import org.hyperic.sigar.ProcCpu;
import org.hyperic.sigar.ProcState;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 删除CPU占用率过高的JAVA进程
 */
@Slf4j
public class JavaProcessKiller {

    static {
        new SigarHunter().init(new Sigar(), SigarHunter.InitType.CpuInfo);
    }

    // 获取所有java进程
    // 获取java进程cpu消耗一直是100的 X秒钟获取一次 历史记录保存Y条
    // 调用cmd指令杀死进程

    Map<Long, ProcCpuRecord> procCpus = new HashMap<>();
    int interval = 3;
    int historyThreshold = 20;
    double cpuThreshold = 45;

    public static void main(String[] args) {

        JavaProcessKiller javaProcessKiller = new JavaProcessKiller();
        if (args.length > 0) {
            javaProcessKiller.interval = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            javaProcessKiller.historyThreshold = Integer.parseInt(args[1]);
        }
        if (args.length > 2) {
            javaProcessKiller.cpuThreshold = Integer.parseInt(args[2]);
        }
        javaProcessKiller.checking();
    }

    public void checking() {

        while (true) {

            log.info("======================");
            getCpuUsage("java");

            procCpus.forEach((pid, procCpu) -> {

                // 添加新记录
                double usage = procCpu.sigarLoadMonitor.updateLoad();
                log.info(" pid: {} cpu: {} ", pid, usage);
                procCpu.procCpus.add(usage);
                // 移除多余的老记录
                while (procCpu.procCpus.size() > historyThreshold) {
                    procCpu.procCpus.remove(0);
                }
                if (procCpu.procCpus.size() == historyThreshold) {

                    // 计算平均CPU
                    double avgCpu = procCpu.procCpus.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
                    log.info("pid: {} avgCpu: {}", pid, avgCpu);
                    // 杀死cpu占用高的进程
                    if (avgCpu >= cpuThreshold) {
                        killProcess(pid);
                    }
                }
            });

            try {
                Thread.sleep(interval * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    public void killProcess(Long pid) {
        new Thread() {
            @Override
            public void run() {

                try {
                    log.warn("kill process {}", pid);
                    Process process = Runtime.getRuntime().exec("taskkill /F /PID " + pid + " /T");
                    Thread.sleep(3000);
                    process.destroy();
                } catch (Exception e) {
                    log.warn("kill process failed", e);
                }
            }
        }.start();
    }

    // TASKKILL /F /PID 1230 /PID 1241 /PID 1253 /T
    public void getCpuUsage(String namePatten) {

        try {
            Sigar sigar = new Sigar();

            List<Long> currPids = new ArrayList<>();
            long[] pids = sigar.getProcList();
            Map<Long, ProcCpu> cpuUsageByPids = new HashMap();
            for (long pid : pids) {

                try {

                    ProcState procState = sigar.getProcState(pid);

                    if (!procState.getName().equals(namePatten)) {
                        continue;
                    }
                    currPids.add(pid);

                    if (procCpus.get(pid) == null) {

                        ProcCpuRecord cpuRecord = procCpus.get(pid);
                        if (cpuRecord == null) {
                            SigarLoadMonitor sigarLoadMonitor = new SigarLoadMonitor(sigar, pid);
                            cpuRecord = new ProcCpuRecord();
                            cpuRecord.pid = pid;
                            cpuRecord.sigarLoadMonitor = sigarLoadMonitor;
                            procCpus.put(pid, cpuRecord);
                        }
                    }

                } catch (SigarException e) {
                    if (e.getMessage().equals("No such process")) {
                        continue;
                    } else {
                        e.printStackTrace();
                    }
                }
            }

            // 移除已经结束的进程
            List<Long> deadedProcess = new ArrayList<>();
            procCpus.keySet().forEach(key -> {
                if (!currPids.contains(key)) {
                    deadedProcess.add(key);
                }
            });
            deadedProcess.forEach(t -> {
                procCpus.remove(t);
                log.warn("remove dead process record {}", t);
            });
//   System.out.println("total : " + total);
        } catch (Exception e) {
            log.warn("getCpuUsage occur exception", e);
        }

    }

    class ProcCpuRecord {
        long pid;
        List<Double> procCpus = new ArrayList<>();
        SigarLoadMonitor sigarLoadMonitor;

    }
}
