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

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandPackageTest {

	private static CommandPackage create(String args) {
		return new CommandPackage(new CommandSource.OfString(args));
	}

	@Test
	public void noArguments() {
		CommandPackage cmd = new CommandPackage(new CommandSource.OfArray());

		assertFalse(cmd.hasNext());
		assertEquals("", cmd.allRemaining());
		assertFalse(cmd.findHiddenArgument("s"));
		assertThrows(NoSuchElementException.class, cmd::next);
	}

	@Test
	public void oneNormalArg() {
		CommandPackage cmd = create("arg");

		assertTrue(cmd.hasNext());
		assertFalse(cmd.findHiddenArgument("g"));
		assertEquals("arg", cmd.next());
		assertThrows(NoSuchElementException.class, cmd::next);
	}

	@Test
	public void oneNormalArgWithPeek() {
		CommandPackage cmd = create("arg");

		assertTrue(cmd.hasNext());
		assertEquals("arg", cmd.peek());
		assertTrue(cmd.hasNext());
		assertEquals("arg", cmd.next());
		assertThrows(NoSuchElementException.class, cmd::next);
	}

	@Test
	public void oneNormalArgAllRemaining() {
		CommandPackage cmd = create("arg");

		assertEquals("arg", cmd.allRemaining());
		assertThrows(NoSuchElementException.class, cmd::next);
	}

	@Test
	public void oneNormalArgAllRemainingWithPeek() {
		CommandPackage cmd = create("arg");

		assertEquals("arg", cmd.peek());
		assertEquals("arg", cmd.allRemaining());
		assertThrows(NoSuchElementException.class, cmd::next);
	}

	@Test
	public void hiddenArgSurroundedByNormalArgsConsume() {
		CommandPackage cmd = create("arg1 -s arg2");

		assertFalse(cmd.findHiddenArgument("s"), "-s is not yet visible");
		assertFalse(cmd.findHiddenArgument("g"), "No such -g specified");

		assertEquals("arg1", cmd.peek());
		assertEquals("arg1", cmd.next());

		assertTrue(cmd.findHiddenArgument("s"), "-s is now visible, having been passed");
		assertFalse(cmd.findHiddenArgument("g"), "No such -g specified");
	}

	@Test
	public void hiddenArgSurroundedByNormalArgsAllRemaining() {
		CommandPackage cmd = create("arg1 -s arg2");

		assertFalse(cmd.findHiddenArgument("s"), "-s is not yet visible");
		assertFalse(cmd.findHiddenArgument("g"), "No such -g specified");

		assertEquals("arg1", cmd.peek());
		assertEquals("arg1 -s arg2", cmd.allRemaining());
	}

	@Test
	public void hiddenArgOnlyCanBeFound() {
		CommandPackage cmd = create("-s");

		assertTrue(cmd.findHiddenArgument("s"), "Hidden argument can be found even if no normal arguments exist");
	}

	@Test
	public void hiddenArgOnlyHasNoNormalArguments() {
		CommandPackage cmd = create("-s");

		assertFalse(cmd.hasNext(), "Hidden arguments are not visible through iteration");
		assertEquals("", cmd.allRemaining());
	}

	@Test
	public void unrecognizedDashArgumentIsOrdinaryArgument() {
		CommandPackage cmd = create("-gav");

		assertTrue(cmd.hasNext(), "An unrecognized argument beginning with a dash is an ordinary argument");
		assertEquals("-gav", cmd.next());
		assertFalse(cmd.findHiddenArgument("gav"));
	}

	@Test
	public void recognizedDashArgumentWithValueIsStillHidden() {
		CommandPackage cmd = create("-scope=server:lobby");

		assertFalse(cmd.hasNext(), "Recognized hidden arguments are not visible through iteration");
		assertEquals("server:lobby", cmd.findHiddenArgumentSpecifiedValue("scope"));
	}

	@Test
	public void playerNameBeginningWithDash() {
		CommandPackage cmd = create("-xLinAwA 7d cheating");

		assertTrue(cmd.hasNext());
		assertEquals("-xLinAwA", cmd.peek(), "A player name beginning with a dash is not a hidden argument");
		assertEquals("-xLinAwA", cmd.next());
		assertFalse(cmd.findHiddenArgument("xLinAwA"));

		assertEquals("7d cheating", cmd.allRemaining());
	}

	@Test
	public void playerNameBeginningWithDashAlongsideHiddenArgument() {
		CommandPackage cmd = create("-s -xLinAwA 7d cheating");

		assertEquals("-xLinAwA", cmd.next(), "The hidden argument is skipped but the player name is not");
		assertTrue(cmd.findHiddenArgument("s"));
		assertEquals("7d cheating", cmd.allRemaining());
	}

	@Test
	public void loneDashIsOrdinaryArgument() {
		CommandPackage cmd = create("arg1 -");

		assertEquals("arg1", cmd.next());
		assertTrue(cmd.hasNext());
		assertEquals("-", cmd.next());
		assertFalse(cmd.findHiddenArgument(""));
	}

	@Test
	public void multipleNormalAndHiddenArgs() {
		CommandPackage cmd = create("user1 -s 30d teaming -green in kitpvp");

		assertTrue(cmd.hasNext());
		assertEquals("user1", cmd.next());

		assertTrue(cmd.hasNext());
		assertEquals("30d", cmd.next());

		assertTrue(cmd.findHiddenArgument("s"));
		assertFalse(cmd.findHiddenArgument("green"));

		assertEquals("teaming -green in kitpvp", cmd.allRemaining());
		assertFalse(cmd.hasNext());
	}

	@Test
	public void multipleNormalAndHiddenArgsWithPeek() {
		CommandPackage cmd = create("user1 -s 30d teaming -green in kitpvp");

		assertTrue(cmd.hasNext());
		assertEquals("user1", cmd.peek());
		assertEquals("user1", cmd.next());

		assertTrue(cmd.hasNext());
		assertEquals("30d", cmd.peek());
		assertEquals("30d", cmd.next());

		assertTrue(cmd.findHiddenArgument("s"));
		assertFalse(cmd.findHiddenArgument("green"));

		assertEquals("teaming", cmd.peek());
		assertEquals("teaming -green in kitpvp", cmd.allRemaining());
		assertFalse(cmd.hasNext());
	}

	@Test
	public void hiddenArgSpecifiedValue() {
		CommandPackage cmd = create("user1 -s -scope=hello 30d teaming -green in kitpvp");

		assertFalse(cmd.findHiddenArgument("s"), "-s not yet visible");
		assertNull(cmd.findHiddenArgumentSpecifiedValue("scope"), "scope not yet visible");
		assertNull(cmd.findHiddenArgumentSpecifiedValue("track"));

		assertTrue(cmd.hasNext());
		assertEquals("user1", cmd.next());

		assertTrue(cmd.hasNext());
		assertEquals("30d", cmd.next());

		assertTrue(cmd.findHiddenArgument("s"));
		assertEquals("hello", cmd.findHiddenArgumentSpecifiedValue("scope"));
		assertFalse(cmd.findHiddenArgument("green"));
		assertNull(cmd.findHiddenArgumentSpecifiedValue("track"));

		assertEquals("teaming -green in kitpvp", cmd.allRemaining());
		assertFalse(cmd.hasNext());
	}
}
