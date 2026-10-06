/*
 * LibertyBans
 * Copyright © 2026 Anand Beh
 *
 * LibertyBans is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * LibertyBans is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with LibertyBans. If not, see <https://www.gnu.org/licenses/>
 * and navigate to version 3 of the GNU Affero General Public License.
 */

package space.arim.libertybans.core.commands;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A command argument iterator. Defines several means for accessing command arguments: <br>
 * <br>
 * 1. Iterative methods: next, hasNext, and peek.
 * 2. Aggregate methods: allRemaining
 * 3. Hidden argument methods: findHiddenArgument <br>
 * <br>
 * Normal command arguments are accessible via normal iteration (next, hasNext, and peek). <br>
 * <br>
 * There are also hidden arguments, which are not viewable through
 * normal iteration methods. If they have not yet been encountered, hidden arguments are
 * visible through aggregate methods; if they have been encountered, hidden arguments
 * can be accessed with {@link #findHiddenArgument(String)}. A hidden argument is considered
 * "encountered" if the process of iteration has passed the argument in its course.
 *
 */
public final class CommandPackage implements CommandSource {

	private final CommandSource source;
	private final Map<String, String> hiddenArguments;

	/**
	 * The prefix denoting a hidden argument
	 */
	private static final char HIDDEN_ARG_PREFIX = '-';

	/**
	 * The names of the hidden arguments which LibertyBans recognizes, including those
	 * taking a value in the form {@code -name=value}.
	 * <p>
	 * Only arguments with one of these names are hidden; every other argument is an
	 * ordinary argument, even if it begins with {@link #HIDDEN_ARG_PREFIX}. This is
	 * essential because player names may legitimately begin with that character - for
	 * instance, {@code /ban -xLinAwA}. When adding a new hidden argument, its name must
	 * be registered here, or it will be treated as a normal command argument.
	 */
	private static final Set<String> HIDDEN_ARGUMENT_NAMES =
			Set.of("s", "both", "uuid-only", "server", "category", "scope");

	private CommandPackage(CommandSource source, Map<String, String> hiddenArguments) {
		this.source = Objects.requireNonNull(source, "source");
		this.hiddenArguments = hiddenArguments;
	}

    public CommandPackage(CommandSource source) {
		this(source, new HashMap<>());
		movePastHiddenArguments();
    }

	public static CommandPackage ofArray(String...args) {
		return new CommandPackage(new CommandSource.OfArray(args));
	}

	// Maintains the guarantee that iterator never refers to a hidden argument
	private void movePastHiddenArguments() {
		String hiddenArg;
		while (source.hasNext()
				&& !(hiddenArg = source.peek()).isEmpty()
				&& isHiddenArgument(hiddenArg)) {
			String next = source.next();
			assert hiddenArg.equals(next) : "bad impl source";
			// Then parse it and add it to our known collection
			String[] hiddenArgPieces = hiddenArg.substring(1).split("=", 2);
			hiddenArguments.put(
					hiddenArgPieces[0].toLowerCase(Locale.ROOT),
					hiddenArgPieces.length == 2 ? hiddenArgPieces[1] : null
			);
		}
	}

	/**
	 * Determines whether an argument is a hidden argument. An argument is hidden only if
	 * it begins with the hidden argument prefix <i>and</i> its name is one of
	 * {@link #HIDDEN_ARGUMENT_NAMES}. Anything else - such as a player name beginning
	 * with a dash - is an ordinary argument.
	 *
	 * @param argument the argument
	 * @return true if the argument is a hidden argument, false if it is an ordinary argument
	 */
	private static boolean isHiddenArgument(String argument) {
		if (argument.charAt(0) != HIDDEN_ARG_PREFIX) {
			return false;
		}
		int equalsIndex = argument.indexOf('=');
		String name = (equalsIndex == -1) ? argument.substring(1) : argument.substring(1, equalsIndex);
		return HIDDEN_ARGUMENT_NAMES.contains(name.toLowerCase(Locale.ROOT));
	}

	@Override
	public String next() {
		String innerNext = source.next();
		movePastHiddenArguments();
		return innerNext;
	}

	@Override
	public String peek() {
		return source.peek();
	}

	@Override
	public boolean hasNext() {
		return source.hasNext();
	}

	/**
	 * Finds a certain hidden argument. See the class javadoc for the meaning of hidden arguments. <br>
	 * <br>
	 * A hidden argument is an argument beginning with "-" whose name appears in
	 * {@link #HIDDEN_ARGUMENT_NAMES}. Arguments beginning with "-" but not matching a known
	 * hidden argument - such as player names - are ordinary arguments.
	 *
	 * @param argument the hidden argument, excluding any special leading characters. Case insensitive
	 * @return whether the hidden argument is present in the arguments which have been encountered so far
	 */
	public boolean findHiddenArgument(String argument) {
		return hiddenArguments.containsKey(argument);
	}

	/**
	 * Finds a certain hidden argument such as "arg=value" and yields the associated value.
	 * This is akin to using {@link #findHiddenArgument(String)} except the argument is a wildcard
	 * wih respect to the specified value.
	 *
	 * @param argPrefix the first part of the hidden argument, i.e. "arg" in "arg=value"
	 * @return the value if it exists
	 */
	public @Nullable String findHiddenArgumentSpecifiedValue(String argPrefix) {
		return hiddenArguments.get(argPrefix);
	}

	@Override
	public CommandPackage copy() {
		CommandSource source = this.source.copy();
		Map<String, String> hiddenArguments = new HashMap<>(this.hiddenArguments);
		return new CommandPackage(source, hiddenArguments);
	}

	@Override
	public String allRemaining() {
		return source.allRemaining();
	}

	/**
	 * Gets remaining arguments, skipping hidden ones, counting them and getting the last argument
	 *
	 * @return the count and last argument
	 */
	public @Nullable CountAndLast countAndLast() {
		if (!hasNext()) {
			return null;
		}
		int count = 0;
		String last;
		do {
			count++;
			last = next();
		} while (hasNext());
		return new CountAndLast(count, last);
	}

	public record CountAndLast(int count, String last) {}

}
