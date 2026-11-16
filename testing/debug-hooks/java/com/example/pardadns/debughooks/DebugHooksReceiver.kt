package com.example.pardadns.debughooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.example.pardadns.billing.Ownership
import com.example.pardadns.data.repository.AutomationRepository
import com.example.pardadns.data.repository.DnsRepository
import com.example.pardadns.data.repository.FilterListRepository
import com.example.pardadns.data.repository.ProRepository
import com.example.pardadns.data.repository.ProtectionSchedule
import com.example.pardadns.domain.event.VpnEvent
import com.example.pardadns.domain.event.VpnEventBus
import com.example.pardadns.service.PardaVpnService
import com.example.pardadns.service.ProtectionScheduler
import com.example.pardadns.service.VpnServiceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalTime

/** The app's Hilt entry point — no Hilt annotations here (they break debug-only source dirs). */
private typealias DebugHooksEntryPoint = VpnServiceEntryPoint

/**
 * Test-only state control for agents and scripts — debug builds only.
 *
 * Every action replies with a one-line result (printed by `am broadcast` as `data="..."`):
 *
 *   adb shell am broadcast -n com.panda.dns/com.example.pardadns.debughooks.DebugHooksReceiver \
 *       -a pandadns.debug.STATE
 *
 * Actions (prefix `pandadns.debug.`):
 *   STATE                                   one-line summary of app state
 *   PRO       --ez on true|false            force Pro on / off (debug override)
 *   PRO       --ez clear true               drop the override — Pro comes from billing again
 *   BILLING   --es result success|cancel|pending|reset
 *                                           swap Play for a fake; sets what the next "Upgrade to Pro" does.
 *                                           reset = fake owns nothing (then re-queries)
 *   CONNECT   --es provider <id>            connect (app must be in the foreground on Android 12+)
 *   DISCONNECT
 *   PAUSE     --ei seconds <n>              pause for n seconds
 *   RESUME
 *   SCHEDULE  --ez on true|false [--ei start_in <min>] [--ei end_in <min>]
 *                                           window relative to now; defaults: start_in -1, end_in 60
 *   TICK                                    run the schedule boundary handler now
 *   FILTER    --es id <listId> --ez on true|false
 *   TESTLIST  --es domains a.com,b.org      import a local list named "e2e-test" (replaces any previous)
 *   TESTLIST  --ez remove true              remove the "e2e-test" list
 */
class DebugHooksReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, DebugHooksEntryPoint::class.java)
        val result = runCatching { handle(context, ep, intent) }.getOrElse { "ERROR ${it.javaClass.simpleName}: ${it.message}" }
        Log.i(TAG, "${intent.action} -> $result")
        resultData = result
    }

    private fun handle(context: Context, ep: DebugHooksEntryPoint, intent: Intent): String {
        val action = intent.action?.removePrefix(PREFIX) ?: return "ERROR no action"
        return when (action) {
            "STATE" -> state(ep)
            "PRO" -> {
                val pro = ep.proRepository()
                pro.setDebugOverride(if (intent.getBooleanExtra("clear", false)) null else intent.getBooleanExtra("on", true))
                ProtectionScheduler.reschedule(context)
                "OK pro=${pro.isPro.value} override=${pro.debugOverride.value ?: "-"}"
            }
            "BILLING" -> billing(ep, intent.getStringExtra("result") ?: return "ERROR missing --es result")
            "CONNECT" -> connect(context, ep, intent.getStringExtra("provider") ?: return "ERROR missing --es provider")
            "DISCONNECT" -> {
                context.startService(service(context, PardaVpnService.ACTION_DISCONNECT))
                "OK"
            }
            "PAUSE" -> {
                val seconds = intent.getIntExtra("seconds", 10)
                context.startService(service(context, PardaVpnService.ACTION_PAUSE).apply {
                    putExtra(PardaVpnService.EXTRA_PAUSE_UNTIL, System.currentTimeMillis() + seconds * 1000L)
                })
                "OK pausing ${seconds}s"
            }
            "RESUME" -> {
                context.startForegroundService(service(context, PardaVpnService.ACTION_RESUME))
                "OK"
            }
            "SCHEDULE" -> schedule(context, ep, intent)
            "TICK" -> {
                ProtectionScheduler.onScheduleTick(context)
                "OK"
            }
            "FILTER" -> {
                val id = intent.getStringExtra("id") ?: return "ERROR missing --es id"
                ep.filterListRepository().setEnabled(id, intent.getBooleanExtra("on", true))
                "OK (async; poll STATE until busy=0)"
            }
            "TESTLIST" -> testList(ep, intent)
            else -> "ERROR unknown action $action"
        }
    }

    private fun state(ep: DebugHooksEntryPoint): String {
        val vpn = when (val e = ep.vpnEventBus().events.replayCache.firstOrNull()) {
            is VpnEvent.Connected -> "connected(${e.providerName})"
            is VpnEvent.Paused -> "paused(${if (e.bySchedule) "schedule" else "manual"},${(e.untilMillis - System.currentTimeMillis()) / 1000}s)"
            is VpnEvent.Error -> "error(${e.message})"
            VpnEvent.Disconnected, null -> "disconnected"
        }
        val f = ep.filterListRepository().state.value
        val s = ep.automationRepository().schedule.value
        val lists = f.lists.filter { it.enabled }.joinToString(",") { it.name.replace(' ', '_') }.ifEmpty { "-" }
        return "vpn=$vpn pro=${ep.proRepository().isPro.value} filters=${f.activeEntries} lists=$lists " +
            "busy=${f.busyIds.size} schedule=${if (s.enabled) "${s.startMinute}-${s.endMinute}" else "off"} " +
            "billing=${ep.billingManager().backendName} owned=${ep.proRepository().ownership.value}"
    }

    private fun billing(ep: DebugHooksEntryPoint, result: String): String {
        val manager = ep.billingManager()
        manager.useBackend(fake)
        when (result) {
            "reset" -> {
                fake.ownership = Ownership.NONE
                fake.nextResult = FakePlayBilling.Result.SUCCESS
            }
            else -> fake.nextResult = runCatching { FakePlayBilling.Result.valueOf(result.uppercase()) }
                .getOrElse { return "ERROR result must be success|cancel|pending|reset" }
        }
        manager.refresh()
        return "OK billing=${manager.backendName} next=${fake.nextResult} owned=${fake.ownership}"
    }

    private fun connect(context: Context, ep: DebugHooksEntryPoint, id: String): String {
        val repo = ep.dnsRepository()
        val p = (repo.getProviders() + repo.getAvailableProviders()).firstOrNull { it.id == id }
            ?: return "ERROR unknown provider $id"
        if (VpnService.prepare(context) != null) return "ERROR VPN consent not granted — connect once from the UI"
        context.startForegroundService(service(context, PardaVpnService.ACTION_CONNECT).apply {
            putExtra(PardaVpnService.EXTRA_DNS_PRIMARY, p.primaryIp)
            putExtra(PardaVpnService.EXTRA_DNS_SECONDARY, p.secondaryIp)
            putExtra(PardaVpnService.EXTRA_DNS_IPV6, p.primaryIpV6)
            putExtra(PardaVpnService.EXTRA_DNS_DOH_URL, p.dohUrl)
            putExtra(PardaVpnService.EXTRA_PROVIDER_NAME, p.name)
        })
        return "OK connecting ${p.name}"
    }

    private fun schedule(context: Context, ep: DebugHooksEntryPoint, intent: Intent): String {
        val repo = ep.automationRepository()
        if (!intent.getBooleanExtra("on", true)) {
            repo.setSchedule(repo.schedule.value.copy(enabled = false))
        } else {
            val now = LocalTime.now().let { it.hour * 60 + it.minute }
            fun at(offset: Int) = Math.floorMod(now + offset, 1440)
            repo.setSchedule(
                ProtectionSchedule(
                    enabled = true,
                    startMinute = at(intent.getIntExtra("start_in", -1)),
                    endMinute = at(intent.getIntExtra("end_in", 60)),
                    days = ProtectionSchedule.ALL_DAYS,
                ),
            )
        }
        ProtectionScheduler.reschedule(context)
        return "OK schedule=${repo.schedule.value}"
    }

    private fun testList(ep: DebugHooksEntryPoint, intent: Intent): String {
        val repo = ep.filterListRepository()
        repo.state.value.lists.filter { it.name == TEST_LIST }.forEach { repo.remove(it.id) }
        if (intent.getBooleanExtra("remove", false)) return "OK removed"
        val domains = intent.getStringExtra("domains")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: return "ERROR missing --es domains"
        val text = domains.joinToString("\n")
        repo.importFromStream(TEST_LIST) { text.byteInputStream() }
        return "OK importing ${domains.size} domain(s) (async; poll STATE until busy=0)"
    }

    private fun service(context: Context, action: String) =
        Intent(context, PardaVpnService::class.java).setAction(action)

    companion object {
        private const val TAG = "PardaDNS/DebugHooks"
        private const val PREFIX = "pandadns.debug."
        private const val TEST_LIST = "e2e-test"

        /** Lives for the process, like the real billing client. */
        private val fake = FakePlayBilling()
    }
}
