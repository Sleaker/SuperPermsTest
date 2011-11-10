package cc.co.evenprime.bukkit.superpermstest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 
 * PerformanceTest
 * 
 * Check various player events for their plausibility and log/deny them/react to
 * them based on configuration
 */
public class SuperPermsTest extends JavaPlugin {

	int defaultSampleSize = 10000;

	private class Testcase {
		public final String name;
		public final String[] perms;
		public final boolean[] results;

		public Testcase(String name, String[] perms, boolean[] results) {
			this.name = name;
			this.perms = perms;
			this.results = results;
		}
	}

	private List<Testcase> testcases = new ArrayList<Testcase>();

	public void onEnable() {

		this.testcases.add(new Testcase("test1", new String[] { "layer11",
				"layer21", "layer31", "layer41" }, new boolean[] { false,
				false, false, false }));

		this.testcases.add(new Testcase("test2", new String[] { "layer12",
				"layer22", "layer32", "layer42" }, new boolean[] { true, true,
				true, true }));

		this.testcases.add(new Testcase("test3", new String[] { "layer13",
				"layer23", "layer33", "layer43" }, new boolean[] { true, true,
				true, true }));

		this.testcases.add(new Testcase("test4", new String[] { "layer14",
				"layer24", "layer34", "layer44" }, new boolean[] { false, true,
				true, true }));

		this.testcases.add(new Testcase("test5", new String[] { "layer15",
				"layer25", "layer35", "layer45" }, new boolean[] { false,
				false, true, true }));

		this.testcases.add(new Testcase("test6", new String[] { "layer16",
				"layer26", "layer36", "layer46" }, new boolean[] { false,
				false, false, true }));

		this.testcases.add(new Testcase("test7", new String[] { "layer17",
				"layer27", "layer37", "layer47" }, new boolean[] { true, false,
				false, false }));

		this.testcases.add(new Testcase("test8", new String[] { "layer18",
				"layer28", "layer38", "layer48" }, new boolean[] { true, false,
				true, true }));

		this.testcases.add(new Testcase("test9", new String[] { "single1",
				"single2" }, new boolean[] { true, false }));

		this.testcases.add(new Testcase("test10", new String[] { "undefi1",
				"undefi2" }, new boolean[] { true, false }));

		System.out.println("Performance Test enabled");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args) {

		Player player = null;
		int sampleSize = defaultSampleSize;

		// Get the corresponding player
		if (args.length > 0) {
			player = Bukkit.getPlayer(args[0]);
		} else {
			sender.sendMessage("Command needs a player name as parameter.");
			return true;
		}

		if (player == null) {
			sender.sendMessage("Player " + args[0] + " isn't online.");
			return true;
		}

		if (args.length > 1) {
			try {
				sampleSize = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				sender.sendMessage(args[1]
						+ " is not an integer. Using default sample size of "
						+ defaultSampleSize);
			}
		} else {
			sender.sendMessage("Using default sample size of "
					+ defaultSampleSize);
		}

		/** CORRECTNESS TEST **/

		// Each permission asked one time, checked against expected results
		boolean failed = false;
		for (Testcase testcase : testcases) {
			failed = correctnessTest(sender, player, testcase) || failed;
		}

		// At least one of the correctness tests failed, so makes no
		// sense to advance
		if (failed)
			return true;

		/** CORRECTNESS TEST END **/

		// Now "warm up" the permissions plugin, that means fill caches,
		// if existing, by asking for each permission 100 times (should
		// be reasonable fast on any server)
		for (int i = 0; i < 100; i++) {
			for (Testcase testcase : testcases) {

				String[] perms = testcase.perms;

				for (int j = 0; j < perms.length; j++) {
					player.hasPermission(perms[j]);
				}
			}
		}

		/** PERFORMANCE TEST **/

		// now do the actual performance tests in order
		for (Testcase testcase : testcases) {
			performanceTestSequential(sender, player, testcase, sampleSize);
		}

		// To finish it off, do the "random" test which will pick random
		// permissions and test them
		performanceTestRandom(sender, player, testcases, sampleSize);

		/** PERFORMANCE TEST END **/
		return true;
	}

	private boolean correctnessTest(CommandSender sender, Player player,
			Testcase testcase) {
		String[] perms = testcase.perms;
		boolean[] results = testcase.results;

		boolean failed = false;

		for (int i = 0; i < perms.length; i++) {
			if (player.hasPermission(perms[i]) != results[i]) {
				// If it doesn't deliver correct answers, we stop right here
				sender.sendMessage("Failed " + testcase.name + ": " + perms[i]
						+ " should be " + results[i] + " but was "
						+ !results[i]
						+ "! Please review your permissions setup.");
				failed = true;
			}
		}

		return failed;
	}

	private void performanceTestSequential(CommandSender sender, Player player,
			Testcase testcase, int samplesize) {

		String[] perms = testcase.perms;
		boolean[] results = testcase.results;
		String message = testcase.name + ": ";

		for (int i = 0; i < perms.length; i++) {
			long time = runtest(player, perms[i], samplesize);
			message += perms[i] + "(" + (results[i] ? "T" : "F") + "): " + time
					+ " ns ";
		}
		sender.sendMessage(message);
	}

	private void performanceTestRandom(CommandSender sender, Player player,
			List<Testcase> testcases, int samplesize) {

		// Set up an array of "samplesize" size and fill it with
		// random permissions from the testcases
		String[] entries = new String[samplesize];
		List<String> perms = new ArrayList<String>();

		for (Testcase testcase : testcases) {
			for (String perm : testcase.perms) {
				perms.add(perm);
			}
		}

		Random generator = new Random();

		for (int i = 0; i < entries.length; i++) {
			entries[i] = perms.get(generator.nextInt(perms.size()));
		}

		// Now run the test
		long startTime = System.nanoTime();
		for (int i = 0; i < entries.length; i++) {
			player.hasPermission(entries[i]);
		}

		long time = (System.nanoTime() - startTime) / samplesize;

		sender.sendMessage("Random test: " + time + " ns");
	}

	private long runtest(Player player, String permission, int samplesize) {
		long startTime = System.nanoTime();
		for (int i = 0; i < samplesize; i++) {
			player.hasPermission(permission);
		}
		return (System.nanoTime() - startTime) / samplesize;
	}

	public void onDisable() {
		// TODO Auto-generated method stub
	}
}
