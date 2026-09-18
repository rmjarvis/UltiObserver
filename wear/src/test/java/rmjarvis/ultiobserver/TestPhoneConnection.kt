package rmjarvis.ultiobserver

import org.junit.Assert.*
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.*

/** Tests related to startup and synchronizing the connection with the phone. */
class TestPhoneConnection {
    /** Connect, receive phone changes, and complete the ordinary watch actions. */
    @Test
    fun gameActions() {
        val phone = PhoneSession()
        phone.connect()
        assertEquals(ConnectionState.CONNECTED, phone.connections.last())
        assertEquals(0L, phone.states.last().phoneClockOffsetMillis)

        // A watch goal carries the displayed token and selected team, and completes only when
        // its acknowledgement arrives with authoritative phone state.
        var applied: Boolean? = null
        phone.controller.recordGoal(TeamId.TEAM_ONE, "playing") { applied = it }
        val goal = WearProtocolCodec.decode(WearGoalRequest.serializer(), phone.command().arguments)
        assertEquals("playing", goal.stateToken)
        assertEquals(TeamId.TEAM_ONE, goal.scoringTeam)
        assertNull(applied)
        phone.complete(true)
        assertEquals(true, applied)
        assertTrue(phone.timers.last().cancelled)

        // Undo and confirmation responses use the same retained-acknowledgement channel.
        phone.controller.undo("playing") { applied = it }
        assertEquals(WearRequestAction.UNDO.path, phone.sent.last().path)
        phone.complete(true)
        assertEquals(true, applied)
        phone.controller.resolveDecision("playing", false) { applied = it }
        val response = WearProtocolCodec.decode(WearDecisionRequest.serializer(), phone.command().arguments)
        assertFalse(response.accept)
        phone.complete(false)
        assertEquals(false, applied)

        // Redo carries the displayed state token and waits for the phone's acknowledgement too.
        applied = null
        phone.controller.redo("undone") { applied = it }
        assertEquals(WearRequestAction.REDO.path, phone.sent.last().path)
        val redo = WearProtocolCodec.decode(WearRedoRequest.serializer(), phone.command().arguments)
        assertEquals("undone", redo.stateToken)
        assertNull(applied)
        phone.complete(true)
        assertEquals(true, applied)

        // The next confirmation is accepted by the phone; its acknowledgement releases the
        // watch with a successful result.
        phone.controller.resolveDecision("playing", true) { applied = it }
        phone.complete(true)
        assertEquals(true, applied)

        // Countdown buttons carry the exact displayed action and use the same acknowledgement.
        phone.controller.countdownAction("playing", WearCountdownAction.START_MISCONDUCT) { result, _ ->
            applied = result
        }
        assertEquals(WearRequestAction.COUNTDOWN.path, phone.sent.last().path)
        val countdown = WearProtocolCodec.decode(
            WearCountdownActionRequest.serializer(), phone.command().arguments,
        )
        assertEquals("playing", countdown.stateToken)
        assertEquals(WearCountdownAction.START_MISCONDUCT, countdown.action)
        phone.complete(true)
        assertEquals(true, applied)

        // A rejected restart releases its button without pretending the countdown started.
        phone.controller.countdownAction("playing", WearCountdownAction.RESTART_PULL) { result, _ ->
            applied = result
        }
        phone.complete(false)
        assertEquals(false, applied)

        // Request a timeout prompt, then send its confirmation back unchanged.
        val confirmation = WearActionConfirmation.Timeout(
            "playing", TeamId.TEAM_TWO, phone.now,
            WearPromptSnapshot("Timeout", emptyList(), "OK", "Cancel", WearGuidancePresentation.VISIBLE, null),
        )
        var prompt: WearTeamActionPrompt? = null
        phone.controller.requestTeamAction(TeamId.TEAM_TWO, "playing", WearTeamAction.Timeout) { prompt = it }
        phone.complete(true, confirmation)
        assertEquals(confirmation, prompt)
        phone.controller.confirmAction(confirmation) { applied = it }
        assertEquals(confirmation, WearProtocolCodec.decode(
            WearConfirmActionRequest.serializer(), phone.command().arguments,
        ).confirmation)
        phone.complete(true)
        assertEquals(true, applied)

        // Water break returns a confirmation through the countdown command without applying it.
        val waterBreak = WearActionConfirmation.WaterBreak(
            "playing", WearPromptSnapshot("Water break", emptyList(), "OK", "Cancel",
                WearGuidancePresentation.VISIBLE, null),
        )
        phone.controller.countdownAction("playing", WearCountdownAction.WATER_BREAK) { result, nextPrompt ->
            applied = result
            prompt = nextPrompt
        }
        phone.complete(false, waterBreak)
        assertEquals(false, applied)
        assertEquals(waterBreak, prompt)

        // Numbered card handoff and cancellation preserve the phone workflow's identity.
        phone.controller.startCardEntry(TeamId.TEAM_ONE, "playing", CardType.YELLOW, "17") { applied = it }
        assertEquals("17", WearProtocolCodec.decode(WearCardEntryRequest.serializer(), phone.command().arguments).jerseyNumber)
        phone.complete(true)
        assertEquals(true, applied)
        phone.controller.cancelCardEntry(TeamId.TEAM_ONE, "playing", CardType.YELLOW, "17") { applied = it }
        assertEquals("17", WearProtocolCodec.decode(WearCancelCardEntryRequest.serializer(), phone.command().arguments).jerseyNumber)
        phone.complete(true)
        assertEquals(true, applied)

        // A phone-initiated change updates the watch without a pending command. Repeated delivery
        // cannot notify the screen twice.
        phone.snapshot = phone.snapshot.copy(sequenceNumber = phone.snapshot.sequenceNumber + 1)
        phone.publish(null)
        val count = phone.states.size
        phone.publish(null)
        assertEquals(count, phone.states.size)
        assertEquals(phone.snapshot, phone.states.last().snapshot)
    }

    /** Keep repeated submissions from replacing an outstanding command or releasing its UI. */
    @Test
    fun repeatedSubmissions() {
        val phone = PhoneSession()
        phone.connect()
        var firstResult: Boolean? = null
        var repeatedResult: Boolean? = null

        // Two taps on Goal can arrive before the watch redraws its disabled controls. Only the
        // first gets sent; the second must not replace its request identity, timer, or callback.
        phone.controller.recordGoal(TeamId.TEAM_ONE, "playing") { firstResult = it }
        val requestId = phone.command().requestId
        val timerCount = phone.timers.size
        phone.controller.recordGoal(TeamId.TEAM_ONE, "playing") { repeatedResult = it }
        assertEquals(1, phone.sent.size)
        assertEquals(requestId, phone.command().requestId)
        assertEquals(timerCount, phone.timers.size)
        assertNull(firstResult)
        assertNull(repeatedResult)

        // Acknowledgement finishes only the original submission. The ignored tap cannot clear
        // pending UI early, and cannot be replayed when the first command completes.
        phone.complete(true)
        assertEquals(true, firstResult)
        assertNull(repeatedResult)
        assertTrue(phone.timers.last().cancelled)
        assertEquals(1, phone.sent.size)

        // Once that command finishes, a new user action is accepted normally.
        var undoResult: Boolean? = null
        phone.controller.undo("playing") { undoResult = it }
        assertEquals(2, phone.sent.size)
        phone.complete(true)
        assertEquals(true, undoResult)
    }

    /** Let authoritative phone changes supersede pending commands without restoring old prompts. */
    @Test
    fun crossedPhoneChanges() {
        val phone = PhoneSession()
        phone.connect()
        val playing = phone.snapshot.activeGame!!
        var result: Boolean? = null

        // The phone changes only display state while a goal is pending. The same game token does
        // not complete the goal; its later retained acknowledgement does, without replaying UI.
        phone.controller.recordGoal(TeamId.TEAM_ONE, "playing") { result = it }
        val goalId = phone.command().requestId
        phone.snapshot = phone.snapshot.copy(sequenceNumber = 2)
        phone.publish(null)
        assertNull(result)
        phone.snapshot = phone.snapshot.copy(sequenceNumber = 3)
        phone.publish(WearCommandAcknowledgement(goalId, true, "phone", 2, null))
        assertEquals(false, result)
        assertEquals(3L, phone.states.last().snapshot.sequenceNumber)

        // A phone action changes the actual game token before the pending watch Undo is processed.
        // The new state releases Undo immediately. Its delayed failure cannot disturb that state.
        result = null
        phone.controller.undo("playing") { result = it }
        val undo = phone.sent.last()
        phone.snapshot = phone.snapshot.copy(
            sequenceNumber = 4, activeGame = phone.snapshot.activeGame!!.copy(stateToken = "phone-goal"),
        )
        phone.publish(null)
        assertEquals(false, result)
        undo.failure()
        assertEquals(ConnectionState.CONNECTED, phone.connections.last())

        // Leaving the game altogether also supersedes a pending watch command.
        result = null
        phone.controller.resolveDecision("phone-goal", false) { result = it }
        phone.snapshot = phone.snapshot.copy(sequenceNumber = 5, status = WearSnapshotStatus.NO_ACTIVE_GAME, activeGame = null)
        phone.publish(null)
        assertEquals(false, result)
        assertNull(phone.states.last().snapshot.activeGame)

        // A new startup can finish an outstanding request without replaying it. A disabled
        // response during a later retry must likewise release that request's UI callback.
        phone.snapshot = phone.snapshot.copy(
            sequenceNumber = 6, status = WearSnapshotStatus.ACTIVE_GAME, activeGame = playing,
        )
        phone.publish(null)
        phone.controller.startCardEntry(TeamId.TEAM_ONE, "playing", CardType.YELLOW, "17") { result = it }
        phone.controller.retry()
        phone.lookups.last().success(listOf(PhoneNode("phone", true)))
        phone.reply(true)
        phone.publish(WearStartupAcknowledgement(phone.startupId()))
        assertEquals(false, result)
        phone.controller.cancelCardEntry(TeamId.TEAM_ONE, "playing", CardType.YELLOW, "17") { result = it }
        phone.controller.retry()
        phone.lookups.last().success(listOf(PhoneNode("phone", true)))
        phone.reply(false)
        assertEquals(false, result)
        assertEquals(ConnectionState.DISABLED, phone.connections.last())

        // In a fresh session, the phone scores before processing a watch goal. Its earlier update
        // is not delivered separately: the newer score arrives with the rejection acknowledgement.
        // The watch must report rejection and adopt that score without waiting for its timeout.
        val rejected = PhoneSession()
        rejected.connect()
        result = null
        rejected.controller.recordGoal(TeamId.TEAM_ONE, "playing") { result = it }
        rejected.snapshot = rejected.snapshot.copy(
            sequenceNumber = 2,
            activeGame = playing.copy(
                stateToken = "phone-goal",
                teamOne = playing.teamOne.copy(score = 1),
                undoDescription = "Goal by Animal",
            ),
        )
        assertNull(result)
        rejected.complete(false)
        assertEquals(false, result)
        assertEquals(rejected.snapshot, rejected.states.last().snapshot)
        assertEquals(1, rejected.states.last().snapshot.activeGame!!.teamOne.score)
        assertTrue(rejected.timers.last().cancelled)

        // The phone undoes that goal while the watch's Undo is in flight. Receiving the restored
        // score with the rejection completes the watch request without undoing anything else.
        result = null
        rejected.controller.undo("phone-goal") { result = it }
        rejected.snapshot = rejected.snapshot.copy(sequenceNumber = 3, activeGame = playing)
        assertNull(result)
        rejected.complete(false)
        assertEquals(false, result)
        assertEquals(rejected.snapshot, rejected.states.last().snapshot)
        assertEquals(0, rejected.states.last().snapshot.activeGame!!.teamOne.score)
        assertTrue(rejected.timers.last().cancelled)

        // A timeout confirmation refers to the score before the phone's next goal. The rejection
        // and new score arrive together; the watch must not treat the timeout as recorded.
        val confirmation = WearActionConfirmation.Timeout(
            "playing", TeamId.TEAM_TWO, rejected.now,
            WearPromptSnapshot(
                "Timeout", emptyList(), "OK", "Cancel", WearGuidancePresentation.VISIBLE, null,
            ),
        )
        result = null
        rejected.controller.confirmAction(confirmation) { result = it }
        rejected.snapshot = rejected.snapshot.copy(
            sequenceNumber = 4,
            activeGame = playing.copy(
                stateToken = "next-phone-goal",
                teamOne = playing.teamOne.copy(score = 1),
                undoDescription = "Goal by Animal",
            ),
        )
        assertNull(result)
        rejected.complete(false)
        assertEquals(false, result)
        assertEquals(rejected.snapshot, rejected.states.last().snapshot)
        assertTrue(rejected.timers.last().cancelled)

        // Another phone goal precedes the watch's card handoff. The returned state rejects the
        // obsolete handoff and leaves no card entry active on the phone.
        result = null
        rejected.controller.startCardEntry(
            TeamId.TEAM_ONE, "next-phone-goal", CardType.YELLOW, "17",
        ) { result = it }
        rejected.snapshot = rejected.snapshot.copy(
            sequenceNumber = 5,
            activeGame = rejected.snapshot.activeGame!!.copy(
                stateToken = "second-phone-goal",
                teamOne = playing.teamOne.copy(score = 2),
            ),
        )
        assertNull(result)
        rejected.complete(false)
        assertEquals(false, result)
        assertEquals(rejected.snapshot, rejected.states.last().snapshot)
        assertNull(rejected.states.last().snapshot.activeGame!!.phoneCardEntry)
        assertTrue(rejected.timers.last().cancelled)

        // The observer retries handoff against the current score. With that entry active, the
        // phone records the card before the watch's cancellation arrives. Its rejection must
        // preserve the completed card state rather than restore the entry.
        result = null
        rejected.controller.startCardEntry(
            TeamId.TEAM_ONE, "second-phone-goal", CardType.YELLOW, "17",
        ) { result = it }
        rejected.snapshot = rejected.snapshot.copy(
            sequenceNumber = 6,
            activeGame = rejected.snapshot.activeGame!!.copy(
                actionsAvailable = false,
                phoneCardEntry = WearPhoneCardEntrySnapshot(TeamId.TEAM_ONE, CardType.YELLOW, "17"),
            ),
        )
        rejected.complete(true)
        assertEquals(true, result)
        result = null
        rejected.controller.cancelCardEntry(
            TeamId.TEAM_ONE, "second-phone-goal", CardType.YELLOW, "17",
        ) { result = it }
        rejected.snapshot = rejected.snapshot.copy(
            sequenceNumber = 7,
            activeGame = rejected.snapshot.activeGame!!.copy(
                stateToken = "card-recorded",
                actionsAvailable = true,
                phoneCardEntry = null,
                undoDescription = "Yellow card",
                teamOne = rejected.snapshot.activeGame!!.teamOne.copy(
                    actions = playing.teamOne.actions.copy(cardLabel = "Card (1)"),
                ),
            ),
        )
        assertNull(result)
        rejected.complete(false)
        assertEquals(false, result)
        assertEquals(rejected.snapshot, rejected.states.last().snapshot)
        assertNull(rejected.states.last().snapshot.activeGame!!.phoneCardEntry)
        assertEquals("Card (1)", rejected.states.last().snapshot.activeGame!!.teamOne.actions.cardLabel)
        assertTrue(rejected.timers.last().cancelled)
    }

    /** Reject taps queued just before phone loss, for each watch action's callback type. */
    @Test
    fun actionsAfterConnectionLoss() {
        val phone = PhoneSession()
        phone.connect()
        phone.controller.phoneReachabilityChanged(emptyList())
        var result: Boolean? = null

        // A tap already queued when the phone vanishes must release the screen's local guard,
        // whether it was a goal, Undo, or a confirmation response.
        phone.controller.recordGoal(TeamId.TEAM_ONE, "playing") { result = it }
        assertEquals(false, result)
        result = null
        phone.controller.undo("playing") { result = it }
        assertEquals(false, result)
        result = null
        phone.controller.resolveDecision("playing", true) { result = it }
        assertEquals(false, result)
        val confirmation = WearActionConfirmation.Timeout(
            "playing", TeamId.TEAM_TWO, phone.now,
            WearPromptSnapshot("Timeout", emptyList(), "OK", "Cancel", WearGuidancePresentation.VISIBLE, null),
        )
        result = null
        phone.controller.confirmAction(confirmation) { result = it }
        assertEquals(false, result)

        // Team-action requests return no prompt. Card handoff and cancellation also fail cleanly;
        // none of these queued taps is sent to a node that is no longer reachable.
        var prompt: WearTeamActionPrompt? = confirmation
        phone.controller.requestTeamAction(TeamId.TEAM_TWO, "playing", WearTeamAction.Timeout) { prompt = it }
        assertNull(prompt)
        result = null
        phone.controller.startCardEntry(TeamId.TEAM_TWO, "playing", CardType.YELLOW, "17") { result = it }
        assertEquals(false, result)
        result = null
        phone.controller.cancelCardEntry(TeamId.TEAM_TWO, "playing", CardType.YELLOW, "17") { result = it }
        assertEquals(false, result)
        assertTrue(phone.sent.isEmpty())
    }

    /** Distinguish a disabled phone from failed discovery, and reconnect after a lost capability. */
    @Test
    fun connectionRecovery() {
        val phone = PhoneSession()
        phone.controller.start()
        phone.lookups.last().success(emptyList())
        assertEquals(ConnectionState.DISCONNECTED, phone.connections.last())

        // Retry finds the nearby phone in preference to a remotely reachable node. A disabled
        // reply completes immediately without waiting for a published snapshot.
        phone.controller.retry()
        phone.lookups.last().success(listOf(PhoneNode("remote", false), PhoneNode("phone", true)))
        assertEquals("phone", phone.startups.last().node)
        phone.reply(false)
        assertEquals(ConnectionState.DISABLED, phone.connections.last())
        assertTrue(phone.states.isEmpty())

        // Enabling Wear and retrying establishes a session even when only a remote node is
        // reachable. The snapshot may arrive before the timing reply.
        phone.controller.retry()
        phone.lookups.last().success(listOf(PhoneNode("phone", false)))
        phone.publish(WearStartupAcknowledgement(phone.startupId()))
        assertEquals(ConnectionState.CONNECTING, phone.connections.last())
        phone.reply(true)
        assertEquals(ConnectionState.CONNECTED, phone.connections.last())

        // Losing the capability keeps the last score available. Rediscovery begins a fresh
        // startup; repeated capability events neither duplicate it nor replace a connected session.
        phone.controller.phoneReachabilityChanged(emptyList())
        assertEquals(ConnectionState.DISCONNECTED, phone.connections.last())
        assertEquals(phone.snapshot, phone.states.last().snapshot)
        phone.controller.phoneReachabilityChanged(listOf(PhoneNode("phone", true)))
        val requests = phone.startups.size
        phone.controller.phoneReachabilityChanged(listOf(PhoneNode("phone", true)))
        assertEquals(requests, phone.startups.size)
        phone.reply(true)
        phone.publish(WearStartupAcknowledgement(phone.startupId()))
        phone.controller.phoneReachabilityChanged(listOf(PhoneNode("phone", true)))
        assertEquals(requests, phone.startups.size)

        // The phone disappears during startup. Its already-in-flight timing reply and snapshot
        // cannot reconnect the watch or start another request to an unavailable phone.
        phone.controller.retry()
        phone.lookups.last().success(listOf(PhoneNode("phone", true)))
        val lostStartupId = phone.startupId()
        phone.controller.phoneReachabilityChanged(emptyList())
        val beforeLateReply = phone.connections.size
        val beforeLateSnapshot = phone.states.size
        val beforeLateRequests = phone.startups.size
        phone.reply(true)
        phone.publish(WearStartupAcknowledgement(lostStartupId))
        assertEquals(beforeLateReply, phone.connections.size)
        assertEquals(beforeLateSnapshot, phone.states.size)
        assertEquals(beforeLateRequests, phone.startups.size)
        assertEquals(ConnectionState.DISCONNECTED, phone.connections.last())

        // On another attempt, losing the phone precedes a transport failure instead of a reply.
        // That late failure cannot change the already-disconnected state.
        phone.controller.phoneReachabilityChanged(listOf(PhoneNode("phone", true)))
        phone.controller.phoneReachabilityChanged(emptyList())
        val beforeLateFailure = phone.connections.size
        phone.startups.last().failure()
        assertEquals(beforeLateFailure, phone.connections.size)
        assertEquals(ConnectionState.DISCONNECTED, phone.connections.last())

        // A later lookup failure reports loss of connection. A stopped watch ignores capability
        // notifications and late lookup callbacks, then starts normally when reopened.
        phone.controller.retry()
        phone.lookups.last().failure()
        assertEquals(ConnectionState.DISCONNECTED, phone.connections.last())
        phone.controller.retry()
        val lookup = phone.lookups.last()
        phone.controller.stop()
        val count = phone.connections.size
        lookup.success(listOf(PhoneNode("phone", true)))
        lookup.failure()
        phone.controller.phoneReachabilityChanged(listOf(PhoneNode("phone", true)))
        phone.publish(null)
        assertEquals(count, phone.connections.size)
        phone.connect()
        assertEquals(ConnectionState.CONNECTED, phone.connections.last())
    }

    /** Recover uncertain commands without replaying them, and ignore superseded attempts. */
    @Test
    fun requestFailures() {
        val phone = PhoneSession()
        phone.connect()
        var applied: Boolean? = null
        phone.controller.recordGoal(TeamId.TEAM_ONE, "playing") { applied = it }
        val sent = phone.sent.last()
        val commandTimeout = phone.timers.last()

        // A send failure releases the UI and requests current phone state. A timeout queued for
        // that same failed command cannot restart recovery or replay the goal.
        sent.failure()
        assertEquals(false, applied)
        assertTrue(phone.connections.contains(ConnectionState.DISCONNECTED))
        val requests = phone.startups.size
        commandTimeout.action()
        assertEquals(requests, phone.startups.size)
        phone.reply(true)
        phone.publish(WearStartupAcknowledgement(phone.startupId()))
        assertEquals(1, phone.sent.size)

        // A later goal is still pending when the phone disappears. Its send failure releases
        // the action, but does not attempt startup until the phone becomes reachable again.
        applied = null
        phone.controller.recordGoal(TeamId.TEAM_ONE, "playing") { applied = it }
        phone.controller.phoneReachabilityChanged(emptyList())
        val beforeLostCommand = phone.startups.size
        phone.sent.last().failure()
        assertEquals(false, applied)
        assertEquals(ConnectionState.DISCONNECTED, phone.connections.last())
        assertEquals(beforeLostCommand, phone.startups.size)
        phone.controller.phoneReachabilityChanged(listOf(PhoneNode("phone", true)))
        phone.reply(true)
        phone.publish(WearStartupAcknowledgement(phone.startupId()))
        assertEquals(ConnectionState.CONNECTED, phone.connections.last())

        // If delivery succeeds but no acknowledgement arrives, the timer takes the same recovery
        // path. An explicit retry replaces that startup and rejects its late reply and failure.
        phone.controller.undo("playing") { applied = it }
        phone.timers.last().action()
        assertEquals(false, applied)
        val oldStartup = phone.startups.last()
        phone.controller.retry()
        phone.lookups.last().success(listOf(PhoneNode("phone", true)))
        oldStartup.success(WearProtocolCodec.encode(WearStartupResponse.serializer(), WearStartupResponse(true, phone.now, "1.4.0")))
        oldStartup.failure()
        assertEquals(ConnectionState.CONNECTING, phone.connections.last())
        phone.timers.last().action()
        assertEquals(ConnectionState.DISCONNECTED, phone.connections.last())

        // The phone is still discoverable after a startup timeout. A capability notification
        // starts another handshake; a transport failure ends that attempt without losing scores.
        phone.controller.phoneReachabilityChanged(listOf(PhoneNode("phone", true)))
        phone.startups.last().failure()
        assertEquals(ConnectionState.DISCONNECTED, phone.connections.last())
        assertEquals(phone.snapshot, phone.states.last().snapshot)

        // History and countdown failures also release their callbacks without applying anything.
        val retryPhone = PhoneSession()
        retryPhone.connect()
        retryPhone.controller.redo("playing") { applied = it }
        retryPhone.sent.last().failure()
        assertEquals(false, applied)
        retryPhone.reply(true)
        retryPhone.publish(WearStartupAcknowledgement(retryPhone.startupId()))
        retryPhone.controller.redo("playing") { applied = it }
        retryPhone.complete(applied = false)
        assertEquals(false, applied)
        var failedPrompt: WearTeamActionPrompt? = timeoutConfirmation()
        retryPhone.controller.countdownAction("playing", WearCountdownAction.PLUS_FIVE) { result, next ->
            applied = result
            failedPrompt = next
        }
        retryPhone.sent.last().failure()
        assertEquals(false, applied)
        assertNull(failedPrompt)

        // Two overlapping retries cannot let an old lookup change the current attempt.
        phone.controller.retry()
        val oldLookup = phone.lookups.last()
        phone.controller.retry()
        oldLookup.success(emptyList())
        oldLookup.failure()
        assertEquals(ConnectionState.CONNECTING, phone.connections.last())
        phone.lookups.last().success(listOf(PhoneNode("phone", true)))
        val stoppedStartup = phone.startups.last()
        phone.controller.stop()
        stoppedStartup.failure()
        stoppedStartup.success(WearProtocolCodec.encode(WearStartupResponse.serializer(), WearStartupResponse(false, phone.now, "1.4.0")))
        sent.failure()
        assertEquals(ConnectionState.CONNECTING, phone.connections.last())
    }

    /** Reject incompatible data and verify a restarted phone before accepting its new session. */
    @Test
    fun sessionChanges() {
        val phone = PhoneSession()
        phone.connect()

        // Malformed or incompatible publications cannot replace a valid screen.
        phone.controller.receiveStateBytes("not JSON".encodeToByteArray())
        phone.controller.receiveStateBytes(WearProtocolCodec.encode(WearStateUpdate.serializer(),
            WearStateUpdate(phone.snapshot.copy(protocolVersion = WEAR_PROTOCOL_VERSION + 1), null)))
        assertEquals(1, phone.states.size)

        // A different phone process has an independent sequence. Its publication triggers
        // startup verification instead of immediately replacing the previous session.
        phone.snapshot = phone.snapshot.copy(sessionId = "restarted-phone", sequenceNumber = 1)
        phone.publish(null)
        assertEquals(ConnectionState.CONNECTING, phone.connections.last())
        assertEquals("phone", phone.states.last().snapshot.sessionId)
        phone.reply(true)
        phone.publish(WearStartupAcknowledgement(phone.startupId()))
        assertEquals("restarted-phone", phone.states.last().snapshot.sessionId)

        // A startup reply from an incompatible app version cannot establish the connection.
        phone.controller.retry()
        phone.lookups.last().success(listOf(PhoneNode("phone", true)))
        phone.startups.last().success(WearProtocolCodec.encode(WearStartupResponse.serializer(),
            WearStartupResponse(true, phone.now, "1.5.0", WEAR_PROTOCOL_VERSION + 1)))
        assertEquals(ConnectionState.UpdateRequired(false, "1.5.0", "1.4.0"), phone.connections.last())

        // A subsequent publication can restart the incomplete handshake while the phone remains
        // reachable; it still needs a matching startup acknowledgement.
        phone.publish(null)
        assertEquals(ConnectionState.CONNECTING, phone.connections.last())
        phone.reply(true)
        phone.publish(WearStartupAcknowledgement(phone.startupId()))
        assertEquals(ConnectionState.CONNECTED, phone.connections.last())
    }

    /** Guide updates by protocol, accepting different compatible release versions. */
    @Test
    fun versionCompatibility() {
        val phone = PhoneSession()
        phone.connect()

        // Different release labels remain compatible when their protocols match.
        phone.controller.retry()
        phone.lookups.last().success(listOf(PhoneNode("phone", true)))
        phone.startups.last().success(WearProtocolCodec.encode(WearStartupResponse.serializer(),
            WearStartupResponse(true, phone.now, "1.4.1")))
        phone.publish(WearStartupAcknowledgement(phone.startupId()))
        assertEquals(ConnectionState.CONNECTED, phone.connections.last())

        // Either side can be older. Mismatches cancel the timeout and prevent game commands.
        for (protocol in listOf(WEAR_PROTOCOL_VERSION - 1, WEAR_PROTOCOL_VERSION + 1)) {
            phone.controller.retry()
            phone.lookups.last().success(listOf(PhoneNode("phone", true)))
            val request = WearProtocolCodec.decode(WearStartupRequest.serializer(), phone.startups.last().bytes)
            assertEquals(WEAR_PROTOCOL_VERSION, request.protocolVersion)
            phone.startups.last().success(WearProtocolCodec.encode(WearStartupResponse.serializer(),
                WearStartupResponse(true, phone.now, "other-release", protocol)))
            assertEquals(ConnectionState.UpdateRequired(
                updatePhone = protocol < WEAR_PROTOCOL_VERSION,
                phoneVersion = "other-release", watchVersion = "1.4.0",
            ), phone.connections.last())
            assertTrue(phone.timers.last().cancelled)
            var applied = true
            phone.controller.recordGoal(TeamId.TEAM_ONE, "playing") { applied = it }
            assertFalse(applied)
            assertTrue(phone.sent.isEmpty())
        }

        // Retry after the update restores the normal connection.
        phone.controller.retry()
        phone.lookups.last().success(listOf(PhoneNode("phone", true)))
        phone.reply(true)
        phone.publish(WearStartupAcknowledgement(phone.startupId()))
        assertEquals(ConnectionState.CONNECTED, phone.connections.last())
    }

    /** Test receiving the two startup reply components (timing and snapshot) in either order. */
    @Test
    fun startupArrivalOrder() {
        // The direct reply arrives first. Calibration alone does not establish a connection;
        // the matching published snapshot supplies the authoritative state.
        val update = WearStateUpdate(
            WearStateSnapshot(
                status = WearSnapshotStatus.NO_ACTIVE_GAME, activeGame = null,
                sessionId = "phone", sequenceNumber = 1,
            ),
            acknowledgement = WearStartupAcknowledgement("startup"),
        )
        val replyFirst = PendingStartup("startup", 900_000L)
        replyFirst.receiveTiming(1_000_000L, 900_120L)
        assertNull(replyFirst.receivedState)
        replyFirst.receiveSnapshot(update)
        assertEquals(ReceivedState(update.snapshot, 99_940L), replyFirst.receivedState)

        // Reverse the order. The snapshot is held until timing arrives; its arrival time does not
        // enter the clock calculation. Both orders produce the same calibrated state.
        val snapshotFirst = PendingStartup("startup", 900_000L)
        snapshotFirst.receiveSnapshot(update)
        assertNull(snapshotFirst.receivedState)
        snapshotFirst.receiveTiming(1_000_000L, 900_120L)
        assertEquals(replyFirst.receivedState, snapshotFirst.receivedState)

        // While waiting for the reply, a phone change can publish a newer snapshot with the retained
        // startup ID. Keep that newer state even if the original publication arrives again later.
        val delayedReply = PendingStartup("startup", 900_000L)
        delayedReply.receiveSnapshot(update)
        val newer = update.copy(snapshot = update.snapshot.copy(
            status = WearSnapshotStatus.DISABLED, sequenceNumber = 2,
        ))
        delayedReply.receiveSnapshot(newer)
        delayedReply.receiveSnapshot(update)
        delayedReply.receiveTiming(1_000_000L, 900_120L)
        assertEquals(ReceivedState(newer.snapshot, 99_940L), delayedReply.receivedState)

        // A retry must not initialize from a previous attempt's publication, or an update that has
        // no startup acknowledgement. Only the retry's own ID can complete its calibrated state.
        val retry = PendingStartup("retry", 900_000L)
        retry.receiveTiming(1_000_000L, 900_120L)
        retry.receiveSnapshot(update)
        retry.receiveSnapshot(update.copy(acknowledgement = null))
        retry.receiveSnapshot(update.copy(acknowledgement = WearCommandAcknowledgement(
            "earlier-command", true, "phone", 1, null,
        )))
        assertNull(retry.receivedState)
        retry.receiveSnapshot(update.copy(acknowledgement = WearStartupAcknowledgement("retry")))
        assertEquals(replyFirst.receivedState, retry.receivedState)
    }

    @Test
    fun phoneTimeCalibration() {
        // A 120 ms round trip compares the returned phone epoch with the watch at its estimated
        // 60 ms midpoint.
        val offset = calibratePhoneClockOffset(
            requestSentAtWatchEpochMillis = 900_000L,
            responseReceivedAtWatchEpochMillis = 900_120L,
            phoneEpochMillis = 1_000_000L,
        )
        assertEquals(99_940L, offset)

        // Adding that offset to the watch clock produces the estimated current phone time.
        assertEquals(1_000_060L, 900_120L + offset)

        // An odd round trip uses the integral millisecond immediately before the half-millisecond
        // midpoint, matching the precision available from the two device clocks.
        assertEquals(
            99_940L,
            calibratePhoneClockOffset(
                requestSentAtWatchEpochMillis = 900_000L,
                responseReceivedAtWatchEpochMillis = 900_121L,
                phoneEpochMillis = 1_000_000L,
            ),
        )

        // The received-state value keeps the decoded snapshot and its calibration together for
        // the UI, while connection states describe the complete handshake lifecycle.
        val snapshot = WearStateSnapshot(
            status = WearSnapshotStatus.NO_ACTIVE_GAME,
            activeGame = null,
        )
        val receivedState = ReceivedState(snapshot, offset)
        assertEquals(snapshot, receivedState.snapshot)
        assertEquals(offset, receivedState.phoneClockOffsetMillis)
        assertEquals(WEAR_PROTOCOL_VERSION, receivedState.snapshot.protocolVersion)

    }
}

/** In-memory transport that lets each narrative deliver the actual encoded replies in order. */
private class PhoneSession : PhoneTransport {
    class Lookup(val success: (List<PhoneNode>) -> Unit, val failure: () -> Unit)
    class Startup(val node: String, val bytes: ByteArray, val success: (ByteArray) -> Unit, val failure: () -> Unit)
    class Command(val path: String, val bytes: ByteArray, val failure: () -> Unit)
    class Timer(val action: () -> Unit) { var cancelled = false }
    val lookups = mutableListOf<Lookup>()
    val startups = mutableListOf<Startup>()
    val sent = mutableListOf<Command>()
    val timers = mutableListOf<Timer>()
    val states = mutableListOf<ReceivedState>()
    val connections = mutableListOf<ConnectionState>()
    var now = 1_000L
    private val actions = WearTeamActionsSnapshot("Time viol.", "Offsides", "Card", "Tech", "Timeout (2)", true, true, true, true, true, true)
    var snapshot = WearStateSnapshot(
        status = WearSnapshotStatus.ACTIVE_GAME,
        activeGame = WearActiveGameSnapshot(
            leftTeam = TeamId.TEAM_ONE,
            stateToken = "playing", actionsAvailable = true, officialClockOffsetMillis = 0,
            officialTimeZoneId = "UTC",
            rulesReference = emptyList(),
            countdownActions = emptyList(),
            timingControls = null,
            countdown = null,
            teamOne = WearTeamSnapshot("Animal", 0, "Far end", 0, 0, actions, emptyList()),
            teamTwo = WearTeamSnapshot("Viscous Coupling", 0, "Near end", 0, 0, actions, emptyList()),
            pullDirection = WearSnapshotPullDirection.LEFT_TO_RIGHT, ratio = null,
            ratioChooser = null,
            redoAvailable = false,
            undoDescription = null, pendingDecision = null, phoneCardEntry = null,
        ),
        sessionId = "phone", sequenceNumber = 1,
    )
    val controller = PhoneConnectionController(this, "1.4.0", { now }, { states.add(it) }, { connections.add(it) })

    fun connect() {
        controller.start()
        lookups.last().success(listOf(PhoneNode("phone", true)))
        reply(true)
        publish(WearStartupAcknowledgement(startupId()))
    }
    fun startupId(): String = WearProtocolCodec.decode(WearStartupRequest.serializer(), startups.last().bytes).requestId
    fun command(): WearCommandRequest = WearProtocolCodec.decode(WearCommandRequest.serializer(), sent.last().bytes)
    fun reply(enabled: Boolean) {
        startups.last().success(WearProtocolCodec.encode(WearStartupResponse.serializer(), WearStartupResponse(enabled, now, "1.4.0")))
    }
    fun publish(ack: WearAcknowledgement?) {
        controller.receiveStateBytes(WearProtocolCodec.encode(WearStateUpdate.serializer(), WearStateUpdate(snapshot, ack)))
    }
    fun complete(applied: Boolean, prompt: WearTeamActionPrompt? = null) {
        publish(WearCommandAcknowledgement(command().requestId, applied, snapshot.sessionId, snapshot.sequenceNumber, prompt))
    }
    override fun findPhone(onSuccess: (List<PhoneNode>) -> Unit, onFailure: () -> Unit) {
        lookups.add(Lookup(onSuccess, onFailure))
    }
    override fun requestStartup(nodeId: String, bytes: ByteArray, onSuccess: (ByteArray) -> Unit, onFailure: () -> Unit) {
        startups.add(Startup(nodeId, bytes, onSuccess, onFailure))
    }
    override fun sendCommand(nodeId: String, path: String, bytes: ByteArray, onFailure: () -> Unit) {
        assertEquals("phone", nodeId)
        sent.add(Command(path, bytes, onFailure))
    }
    override fun scheduleTimeout(delayMillis: Long, action: () -> Unit): () -> Unit {
        assertEquals(5_000L, delayMillis)
        val timer = Timer(action)
        timers.add(timer)
        return { timer.cancelled = true }
    }
}
