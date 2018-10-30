package org.benjamin.jpk;

import com.component.alarmAgent.common.gatherData.SigarHunter;
import org.hyperic.sigar.ProcCpu;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;

public class SigarLoadMonitor {

    private static final int TOTAL_TIME_UPDATE_LIMIT = 2000;
    private final int cpuCount;
    private Sigar sigar;
    private long pid;
    private ProcCpu prevPc;
    private double load;

    public SigarLoadMonitor(Sigar sigar, long pid) throws SigarException {
//        sigar = new Sigar();
        this.sigar = sigar;
        this.pid = pid;
        cpuCount = sigar.getCpuList().length;
//        pid = sigar.getPid();
        prevPc = sigar.getProcCpu(pid);
        load = 0;
    }
    static {
        new SigarHunter().init(new Sigar(), SigarHunter.InitType.CpuInfo);
    }
    public static void main(String[] args) {
        try {

            SigarLoadMonitor sigarLoadMonitor = new SigarLoadMonitor(new Sigar(), 45992);
            for (int i = 0; i < 1000; i++) {
                System.out.println(sigarLoadMonitor.updateLoad());
                Thread.sleep(3000);
            }
        } catch (Exception e) {

        }
    }

    public double updateLoad() {
        try {
            ProcCpu curPc = sigar.getProcCpu(pid);
            long totalDelta = curPc.getTotal() - prevPc.getTotal();
            long timeDelta = curPc.getLastTime() - prevPc.getLastTime();
            if (totalDelta == 0) {
                if (timeDelta > TOTAL_TIME_UPDATE_LIMIT) {
                    load = 0;
                }
                if (load == 0) {
                    prevPc = curPc;
                }
            } else {
                load = 100. * totalDelta / timeDelta / cpuCount;
                prevPc = curPc;
            }
            return load;
        } catch (SigarException ex) {
            throw new RuntimeException(ex);
        }
    }

    public double getLoad() {
        return load;
    }
}
