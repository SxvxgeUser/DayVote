package net.oldschoolminecraft.dv;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DayVote extends JavaPlugin {


    private static DayVote instance;

    public VoteConfig config;
    private Vote vote;
    private long lastVote;
    private long lastStartVote;
    private long lastRainVote;
    private long lastRainStartVote;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    public DayVoteType voteType;
    public boolean shouldWeatherBeOn = false;

    @Override
    public void onEnable()
    {
        instance = this;
        config = new VoteConfig(new File(getDataFolder(), "config.yml"));
        lastVote = Math.max(0, UnixTime.now() - (long)config.getConfigOption("cooldownSeconds"));
        lastRainVote = Math.max(0, UnixTime.now() - (long)config.getConfigOption("rainCooldownSeconds"));
        voteType = DayVoteType.NONE;
        getCommand("vote").setExecutor(new VoteCommand());
        Bukkit.getServer().getPluginManager().registerEvents(new WeatherHandler(), this);

        System.out.println("DayVote version: "+ getDescription().getVersion() + " enabled!");
        System.out.println("Last Vote Time: " + lastVote);
        System.out.println("Last Rain Vote Time: " + lastRainVote);
        System.out.println("Current Unix Time: " + UnixTime.now());
        System.out.println("Vote Day Cooldown Setting: " + config.getConfigOption("cooldownSeconds"));
        System.out.println("Vote Rain Cooldown Setting: " + config.getConfigOption("rainCooldownSeconds"));
        System.out.println("Can start Day vote? " + (canStartVote() ? "Yes" : "No"));
        System.out.println("Can start Rain vote? " + (canStartRainVote() ? "Yes" : "No"));
    }

    @Override
    public void onDisable()
    {
        forceCancelVote();
        System.out.println("DayVote version: "+ getDescription().getVersion() + " disabled!");
    }

    public Vote getActiveVote() {
        return vote;
    }

    public boolean canVoteRain()
    {
        if (config.getConfigOption("allowRainVote").equals(true))
            return true;
        else return false;
    }

    public void setAllowRainVote(boolean option)
    {
        try
        {
            config.setProperty("allowRainVote", option);
            config.save();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

//  DAY
    public synchronized boolean canStartVote()
    {
        long timeSinceLastVote = (UnixTime.now() - lastVote);
        long cooldown = (long) config.getConfigOption("cooldownSeconds");
        return timeSinceLastVote >= cooldown;
    }

    public long getCooldownTimeLeft()
    {
        long timeSinceLastVote = (UnixTime.now() - lastVote);
        long cooldown = (long) config.getConfigOption("cooldownSeconds");
        return (long) Math.max(0, cooldown-timeSinceLastVote);
    }

    public long getVoteTimeLeft()
    {
        long timeSinceLastVoteStart = (UnixTime.now() - lastStartVote);
        long voteDurationSeconds = (long) config.getConfigOption("voteDurationSeconds");
        return (long) Math.max(0, voteDurationSeconds - timeSinceLastVoteStart);
    }

//  RAIN
    public synchronized boolean canStartRainVote()
    {
        long timeSinceLastRainVote = (UnixTime.now() - lastRainVote);
        long cooldown = (long) config.getConfigOption("rainCooldownSeconds");
        return timeSinceLastRainVote >= cooldown;
    }

    public long getRainCooldownTimeLeft()
    {
        long timeSinceLastRainVote = (UnixTime.now() - lastRainVote);
        long cooldown = (long) config.getConfigOption("rainCooldownSeconds");
        return (long) Math.max(0, cooldown-timeSinceLastRainVote);
    }

    public long getRainVoteTimeLeft()
    {
        long timeSinceLastRainVoteStart = (UnixTime.now() - lastRainStartVote);
        long voteDurationSeconds = (long) config.getConfigOption("voteDurationSeconds");
        return (long) Math.max(0, voteDurationSeconds-timeSinceLastRainVoteStart);
    }

    public String formatTime(final long seconds)
    {
        final long minute = TimeUnit.SECONDS.toMinutes(seconds);
        final long second = TimeUnit.SECONDS.toSeconds(seconds) - TimeUnit.SECONDS.toMinutes(seconds) * 60L;
        return minute + "m" + second + "s";
    }

    public synchronized Vote startNewDayVote()
    {
        if (!canStartVote()) return null;
        vote = new Vote();
        setVoteType(DayVoteType.DAY);
        broadcast(String.valueOf(config.getConfigOption("messages.started")));
        long voteDurationSeconds = (long) config.getConfigOption("voteDurationSeconds");
        scheduler.schedule(this::processDayVote, voteDurationSeconds, TimeUnit.SECONDS);
        lastStartVote = Math.max(0, UnixTime.now());
        return vote;
    }

    public synchronized Vote startNewRainVote()
    {
        if (!canStartRainVote()) return null;
        vote = new Vote();
        setVoteType(DayVoteType.RAIN);
        broadcast(String.valueOf(config.getConfigOption("messages.startedRain")));
        long voteDurationSeconds = (long) config.getConfigOption("voteDurationSeconds");
        scheduler.schedule(this::processRainVote, voteDurationSeconds, TimeUnit.SECONDS);
        lastRainStartVote = Math.max(0, UnixTime.now());
        return vote;
    }

    public synchronized void processDayVote()
    {
        if (vote == null)
        {
            startNewDayVote();
            return;
        }

        if (vote.didVotePass())
        {
            broadcast(String.valueOf(config.getConfigOption("messages.succeeded")));
            Bukkit.getServer().getWorld("world").setTime(0);
        }
        else broadcast(String.valueOf(config.getConfigOption("messages.failed")));
        resetDayVote();
    }

    public synchronized void processRainVote()
    {
        if (vote == null)
        {
            startNewRainVote();
            return;
        }

        if (vote.didRainVotePass())
        {
            if (Bukkit.getServer().getWorld("world").hasStorm())
            {
                broadcast(String.valueOf(config.getConfigOption("messages.alreadyRaining")));
            } else {
                int rainDuration = (int) config.getConfigOption("rainDurationTicks");
                broadcast(String.valueOf(config.getConfigOption("messages.succeededRain")));
                shouldWeatherBeOn = true;
                Bukkit.getServer().getWorld("world").setStorm(true);
                Bukkit.getServer().getWorld("world").setWeatherDuration(rainDuration);
                if (config.getConfigOption("allowThunder").equals(true))
                {
                    int thunderDuration = (int) config.getConfigOption("thunderDurationTicks");
                    Bukkit.getServer().getWorld("world").setThundering(true);
                    Bukkit.getServer().getWorld("world").setThunderDuration(thunderDuration);
                } else Bukkit.getServer().getWorld("world").setThundering(false);
            }
        } else broadcast(String.valueOf(config.getConfigOption("messages.failedRain")));
        resetRainVote();
    }

    private synchronized void resetDayVote()
    {
//        scheduler.shutdown(); //EXPERIMENTAL! FUNCTION HAS NOT BEEN TESTED YET
        vote = null;
        setVoteType(DayVoteType.NONE);
        lastVote = Math.max(0, UnixTime.now());
    }

    private synchronized void resetRainVote()
    {
//        scheduler.shutdown(); //EXPERIMENTAL! FUNCTION HAS NOT BEEN TESTED YET
        vote = null;
        setVoteType(DayVoteType.NONE);
        lastRainVote = Math.max(0, UnixTime.now());
    }

    private synchronized void forceCancelVote()
    {
        if (vote != null)
        {
            resetDayVote();
            resetRainVote();
        }
    }

    private void broadcast(String msg)
    {
        for (Player all : getServer().getOnlinePlayers())
            all.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    public VoteConfig getConfig()
    {
        return config;
    }

    public static DayVote getInstance()
    {
        return instance;
    }

    public void setVoteType(DayVoteType voteType)
    {
        this.voteType = voteType;
    }

    public DayVoteType getVoteType()
    {
        return voteType;
    }
}
