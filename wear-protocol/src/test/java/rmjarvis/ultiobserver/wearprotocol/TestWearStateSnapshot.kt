package rmjarvis.ultiobserver.wearprotocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests related to preparing and receiving tagged updates sent from phone to watch. */
class TestWearStateSnapshot {
    /**
     * Test assignment of session IDs and sequence numbers to outgoing snapshots, including
     * repeated payloads, returning to a previous payload, and restarting the phone session.
     */
    @Test
    fun snapshotTags() {
        // Start with an enabled phone that has no active game. Tagging that same payload again
        // must produce the same snapshot, including the session ID and sequence number.
        val snapshotTagger = WearSnapshotTagger()
        val idle = WearStateSnapshot(status = WearSnapshotStatus.NO_ACTIVE_GAME, activeGame = null)
        val first = snapshotTagger.tag(idle)
        assertEquals(first, snapshotTagger.tag(idle))

        // Disable Wear OS, then return to the original idle state. Each change advances the number.
        val disabled = snapshotTagger.tag(idle.copy(status = WearSnapshotStatus.DISABLED))
        assertTrue(disabled.sequenceNumber > first.sequenceNumber)

        // Restoring the earlier payload does not restore its old number. The session stays the same.
        val restored = snapshotTagger.tag(idle)
        assertTrue(restored.sequenceNumber > disabled.sequenceNumber)
        assertEquals(first.sessionId, restored.sessionId)

        // A new tagger represents a restarted phone session. Its first snapshot starts numbering
        // at the same value as before, but its different session ID distinguishes the two snapshots.
        val restarted = WearSnapshotTagger().tag(idle)
        assertNotEquals(first.sessionId, restarted.sessionId)
        assertEquals(first.sequenceNumber, restarted.sequenceNumber)
    }

    /**
     * Test which incoming snapshot tags the watch accepts: newer snapshots in its current session,
     * duplicate or older deliveries, and snapshots received around a phone-session change.
     * These are receiver inputs, not a simulation of the transport's delivery behavior.
     */
    @Test
    fun snapshotReception() {
        // Establish the receiver from a verified startup snapshot at sequence number 10.
        // Subsequent deliveries will be compared with this session and position in the sequence.
        val startup = WearStateSnapshot(
            status = WearSnapshotStatus.NO_ACTIVE_GAME,
            activeGame = null,
            sessionId = "first-phone-process",
            sequenceNumber = 10,
        )
        var snapshotReceiver = WearSnapshotReceiver(startup)

        // Deliver snapshot 12 before snapshot 11. The newer disabled state must be accepted,
        // and the delayed idle state must neither replace it nor be recognized as current.
        val earlierSnapshot = startup.copy(sequenceNumber = 11)
        val phoneChange = startup.copy(sequenceNumber = 12, status = WearSnapshotStatus.DISABLED)
        assertTrue(snapshotReceiver.receive(phoneChange))
        assertFalse(snapshotReceiver.receive(earlierSnapshot))
        assertEquals(phoneChange, snapshotReceiver.current)

        // Deliver snapshot 12 again, followed by snapshot 13 twice. Only the first delivery of 13
        // should be accepted as a new current snapshot; both duplicate deliveries are rejected.
        assertFalse(snapshotReceiver.receive(phoneChange))
        val nextSnapshot = phoneChange.copy(sequenceNumber = 13)
        assertTrue(snapshotReceiver.receive(nextSnapshot))
        assertEquals(nextSnapshot, snapshotReceiver.current)
        assertFalse(snapshotReceiver.receive(nextSnapshot))

        // A different phone session cannot be established by an ordinary update. Establish it
        // through startup instead, then reject the old session's higher-numbered snapshot while
        // accepting the new session's next number. Numbers are comparable only within a session.
        val restarted = startup.copy(sessionId = "second-phone-process", sequenceNumber = 0)
        assertFalse(snapshotReceiver.receive(restarted))
        snapshotReceiver = WearSnapshotReceiver(restarted)
        assertFalse(snapshotReceiver.receive(nextSnapshot))
        assertTrue(snapshotReceiver.receive(restarted.copy(sequenceNumber = 1)))
    }

    /**
     * Test matching acknowledgements to pending commands independently of snapshot acceptance,
     * including rejected commands, repeated acknowledgements, and watch restarts.
     */
    @Test
    fun commandAcknowledgements() {
        // Establish an idle phone snapshot, then begin a watch command that needs acknowledgement.
        val tagger = WearSnapshotTagger()
        val idle = tagger.tag(WearStateSnapshot(
            status = WearSnapshotStatus.NO_ACTIVE_GAME, activeGame = null,
        ))
        val snapshotReceiver = WearSnapshotReceiver(idle)
        val pending = WearPendingCommand()
        val requestId = pending.begin("game-state")

        // The phone disables Wear OS before acknowledging this command. The newer snapshot is
        // accepted, but an acknowledgement for an earlier command (or no acknowledgement at all)
        // cannot clear the current pending request. New state alone does not prove completion.
        val earlierResult = WearCommandAcknowledgement(
            "earlier-command", true, idle.sessionId, idle.sequenceNumber, null,
        )
        val disabled = tagger.tag(idle.copy(status = WearSnapshotStatus.DISABLED))
        assertTrue(snapshotReceiver.receive(disabled))
        assertFalse(pending.complete(earlierResult))
        assertFalse(pending.complete(null))
        assertFalse(pending.complete(WearStartupAcknowledgement("startup")))
        assertEquals(requestId, pending.requestId)

        // Now the phone rejects the pending command without changing its disabled state. Encode
        // and decode the published update to check both parts survive the wire format. The repeated
        // snapshot is ignored, but the matching acknowledgement clears the pending request even
        // though the action was not applied. Its result still belongs to the current snapshot.
        val result = WearCommandAcknowledgement(
            requestId, false, disabled.sessionId, disabled.sequenceNumber, null,
        )
        val update = WearStateUpdate(disabled, result)
        val received = WearProtocolCodec.decode(
            WearStateUpdate.serializer(), WearProtocolCodec.encode(WearStateUpdate.serializer(), update),
        )
        assertEquals(update, received)
        assertFalse(snapshotReceiver.receive(received.snapshot))
        assertTrue(pending.complete(received.acknowledgement))
        assertTrue(result.matchesSnapshot(snapshotReceiver.current))
        assertNull(pending.requestId)

        // Begin another command. It gets a different request ID, so receiving the previous
        // command's acknowledgement again cannot complete this new request.
        val nextId = pending.begin("game-state")
        assertNotEquals(requestId, nextId)
        assertFalse(pending.complete(result))

        // A restarted watch also creates a distinct request ID rather than reusing an old one.
        // An acknowledgement left over from the earlier watch instance cannot complete its request.
        val restarted = WearPendingCommand()
        assertNotEquals(nextId, restarted.begin("game-state"))
        assertFalse(restarted.complete(result))

        // Abandoning a pending request, as on stop or timeout, clears its local tracking state.
        pending.clear()
        assertNull(pending.requestId)
    }

    /** Test superseding a pending command and ignoring its acknowledgement after moving on. */
    @Test
    fun supersededCommands() {
        // Begin tracking a command against a game token. Checking that same token leaves it pending.
        val pending = WearPendingCommand()
        val deferredCap = pending.begin("cap-notice")
        assertFalse(pending.supersede("cap-notice"))
        assertEquals(deferredCap, pending.requestId)

        // A different token supersedes the command. Checking again has nothing left to clear.
        assertTrue(pending.supersede("cap-accepted"))
        assertNull(pending.requestId)
        assertFalse(pending.supersede("cap-accepted"))

        // The watch can now send another command. The late rejection of Not yet cannot clear it.
        val nextCommand = pending.begin("cap-accepted")
        val rejection = WearCommandAcknowledgement(deferredCap, false, "phone", 2, null)
        assertFalse(pending.complete(rejection))
        assertEquals(nextCommand, pending.requestId)

        // Leaving the game also makes an outstanding game command obsolete.
        assertTrue(pending.supersede(null))
        assertNull(pending.requestId)
    }

    /**
     * Test watch-side handling of a retained acknowledgement when the original command update
     * was not observed. Completion must not make an older command result current again.
     */
    @Test
    fun skippedUpdates() {
        // Establish snapshot 1 and begin a command. Its acknowledgement refers to snapshot 2,
        // but deliver only snapshot 3, carrying that retained acknowledgement. The receiver accepts
        // the newer state and completes the command. The acknowledgement's snapshot identity no
        // longer matches, so its old navigation result must not be applied to the current state.
        val pending = WearPendingCommand()
        val initial = WearStateSnapshot(
            status = WearSnapshotStatus.NO_ACTIVE_GAME, activeGame = null,
            sessionId = "phone", sequenceNumber = 1,
        )
        val snapshotReceiver = WearSnapshotReceiver(initial)
        val result = WearCommandAcknowledgement(pending.begin("game-state"), true, "phone", 2, null)
        val later = WearStateUpdate(initial.copy(sequenceNumber = 3), result)
        assertTrue(snapshotReceiver.receive(later.snapshot))
        assertTrue(pending.complete(later.acknowledgement))
        assertFalse(result.matchesSnapshot(snapshotReceiver.current))

        // Deliver the skipped snapshot 2 afterward, along with the same acknowledgement.
        // Neither is new: the current snapshot remains 3 and the completed command stays cleared.
        assertFalse(snapshotReceiver.receive(initial.copy(sequenceNumber = 2)))
        assertFalse(pending.complete(result))
        assertEquals(later.snapshot, snapshotReceiver.current)
    }
}
