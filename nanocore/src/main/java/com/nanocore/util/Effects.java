package com.nanocore.util;

import com.nanocore.particle.Fx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Running effects: each one is a small function called every server tick (t = 1, 2, 3 ...) until it returns false. */
public final class Effects {
    private Effects() {}

    public interface Tick {
        boolean run(int t);
    }

    /** Lets another piece of code switch an effect off (used by the toggle aura). */
    public static final class Handle {
        public volatile boolean dead = false;
    }

    private static final class Running {
        final Tick tick;
        int t = 0;

        Running(Tick tick) {
            this.tick = tick;
        }
    }

    private static final List<Running> LIST = new ArrayList<>();

    public static void add(Tick tick) {
        LIST.add(new Running(tick));
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (LIST.isEmpty()) return;
            List<Running> snapshot = new ArrayList<>(LIST);
            for (Running r : snapshot) {
                boolean keep;
                try {
                    keep = r.tick.run(++r.t);
                } catch (Exception e) {
                    e.printStackTrace();
                    keep = false;
                }
                if (!keep) LIST.remove(r);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> LIST.clear());
    }

    /** Radius of an expanding ground ring at tick t (fast at first, slowing down). */
    public static double ringRadius(double maxR, int t, int ticks) {
        double k = Math.min(1.0, (double) t / ticks);
        return maxR * (1.0 - (1.0 - k) * (1.0 - k));
    }

    /** A thin flat ring of tiny cubes that expands over the ground, with little flames licking up from it. */
    public static void groundRing(ServerWorld w, Vec3d c, double maxR, int ticks) {
        add(t -> {
            if (t > ticks) return false;
            double r = ringRadius(maxR, t, ticks);
            int n = (int) Math.max(24, Math.min(160, r * 2 * Math.PI / 0.26));
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n + Fx.R.nextDouble() * 0.05;
                Fx.vel(w, "nano_arc", c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 0, 0, 0);
            }
            for (int i = 0; i < Math.max(3, n / 12); i++) {
                double a = Fx.R.nextDouble() * Math.PI * 2;
                Fx.vel(w, "nano_glow", c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r,
                        Math.cos(a) * 0.04, 0.06 + Fx.R.nextDouble() * 0.1, Math.sin(a) * 0.04);
            }
            return true;
        });
    }
}
