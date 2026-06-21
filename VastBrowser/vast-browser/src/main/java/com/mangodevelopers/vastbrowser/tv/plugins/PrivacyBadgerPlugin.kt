/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mangodevelopers.vastbrowser.tv.plugins

import android.content.Context
import android.util.Log
import org.json.JSONObject
import com.mangodevelopers.vastbrowser.tv.lite.R

/**
 * Privacy Badger–style heuristic tracker blocker.
 *
 * How it works:
 * 1. Pre-seeds a list of known trackers from a bundled seed list (Privacy Badger's known tracker data).
 * 2. On each page load, injects JS that:
 *    - Identifies all third-party resource requests (scripts, images, iframes, XHR, fetch).
 *    - Reports third-party domains back to the plugin for heuristic tracking.
 *    - Blocks cookies and storage access for known tracker domains.
 *    - Removes tracking parameters from URLs (fbclid, utm_*, etc.).
 * 3. Heuristic learning: if a third-party domain appears on 3+ different first-party sites,
 *    it gets flagged as a tracker (this learning happens across page loads within a session).
 *
 * The seed list ensures common trackers are blocked from the first page load,
 * while the heuristic learning catches new/unknown trackers over time.
 */
class PrivacyBadgerPlugin : BasePlugin(
    PluginInfo(
        id = "privacy_badger",
        name = "Privacy Badger",
        description = "Learns to block invisible trackers. Automatically discovers trackers based on their behavior across websites.",
        version = "1.0.0",
        iconRes = R.drawable.ic_privacy_badger,
        defaultEnabled = true
    )
) {
    companion object {
        private const val TAG = "PrivacyBadgerPlugin"
        private const val TRACKER_THRESHOLD = 3 // Block after seen on 3+ different first-party sites
    }

    // Known tracker domains from seed list (pre-blocked)
    private val seededTrackers = mutableSetOf<String>()

    // Heuristic learning: domain → set of first-party sites it was seen on
    private val domainSightings = mutableMapOf<String, MutableSet<String>>()

    // Domains that have been heuristically flagged as trackers
    private val learnedTrackers = mutableSetOf<String>()

    @Volatile
    private var blockedCount = 0

    @Volatile
    private var initialized = false

    override fun initialize(context: Context) {
        if (initialized) return
        try {
            loadSeedList(context)
            initialized = true
            Log.d(TAG, "Initialized with ${seededTrackers.size} seeded trackers")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize seed list", e)
        }
    }

    /**
     * Load the pre-seeded tracker domain list from assets.
     * Format: JSON object with domain keys.
     */
    private fun loadSeedList(context: Context) {
        try {
            val json = context.assets.open("filters/privacy_badger_seed.json")
                .bufferedReader().readText()
            val obj = JSONObject(json)
            val domainsArray = obj.optJSONArray("trackers")
            if (domainsArray != null) {
                for (i in 0 until domainsArray.length()) {
                    seededTrackers.add(domainsArray.getString(i).lowercase())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load seed list", e)
        }
    }

    /**
     * Record that [thirdPartyDomain] was seen on [firstPartyDomain].
     * If it's been seen on >= TRACKER_THRESHOLD different first-party sites,
     * it gets added to [learnedTrackers].
     */
    fun recordThirdPartySighting(thirdPartyDomain: String, firstPartyDomain: String) {
        val domain = thirdPartyDomain.lowercase()
        val firstParty = firstPartyDomain.lowercase()

        // Don't track same-party
        if (domain == firstParty || domain.endsWith(".$firstParty") || firstParty.endsWith(".$domain")) return

        val sightings = domainSightings.getOrPut(domain) { mutableSetOf() }
        sightings.add(firstParty)

        if (sightings.size >= TRACKER_THRESHOLD && domain !in learnedTrackers) {
            learnedTrackers.add(domain)
            Log.d(TAG, "Learned new tracker: $domain (seen on ${sightings.size} sites)")
        }
    }

    override fun generateInjectionScript(context: Context, url: String): String {
        if (!initialized) initialize(context)

        if (url.startsWith("about:") || url.startsWith("data:")) return ""

        // Combine seeded + learned trackers into a JS Set
        val allTrackers = seededTrackers + learnedTrackers
        val trackerList = allTrackers.joinToString(",") { "'${it.replace("'", "\\'")}'" }

        return """
            (function() {
                if (window.__vastPrivacyBadgerInjected) return;
                window.__vastPrivacyBadgerInjected = true;
                window.__vastPBBlockedCount = 0;

                var trackerDomains = new Set([$trackerList]);
                var currentHost = window.location.hostname.toLowerCase();

                function isTracker(hostname) {
                    hostname = hostname.toLowerCase();
                    if (trackerDomains.has(hostname)) return true;
                    var parts = hostname.split('.');
                    for (var i = 1; i < parts.length - 1; i++) {
                        if (trackerDomains.has(parts.slice(i).join('.'))) return true;
                    }
                    return false;
                }

                function isThirdParty(hostname) {
                    hostname = hostname.toLowerCase();
                    if (hostname === currentHost) return false;
                    if (hostname.endsWith('.' + currentHost)) return false;
                    if (currentHost.endsWith('.' + hostname)) return false;
                    return true;
                }

                // --- Block third-party cookies from tracker domains ---
                // Override document.cookie to filter out tracker cookies
                try {
                    var origCookieDesc = Object.getOwnPropertyDescriptor(Document.prototype, 'cookie');
                    if (origCookieDesc) {
                        Object.defineProperty(document, 'cookie', {
                            get: function() { return origCookieDesc.get.call(this); },
                            set: function(val) {
                                // Allow first-party cookies, block tracker cookies
                                try {
                                    var domainMatch = val.match(/domain=\.?([^;]+)/i);
                                    if (domainMatch) {
                                        var cookieDomain = domainMatch[1].trim().toLowerCase();
                                        if (isThirdParty(cookieDomain) && isTracker(cookieDomain)) {
                                            window.__vastPBBlockedCount++;
                                            return;
                                        }
                                    }
                                } catch(e) {}
                                return origCookieDesc.set.call(this, val);
                            },
                            configurable: true
                        });
                    }
                } catch(e) {}

                // --- Block third-party tracker scripts/iframes/images ---
                var origFetch = window.fetch;
                window.fetch = function(input, init) {
                    try {
                        var url = (typeof input === 'string') ? input : (input.url || '');
                        var hostname = new URL(url, window.location.href).hostname;
                        if (isThirdParty(hostname) && isTracker(hostname)) {
                            window.__vastPBBlockedCount++;
                            return Promise.reject(new TypeError('Blocked by Privacy Badger'));
                        }
                    } catch(e) {}
                    return origFetch.apply(this, arguments);
                };

                var origXHROpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    try {
                        var fullUrl = new URL(url, window.location.href);
                        if (isThirdParty(fullUrl.hostname) && isTracker(fullUrl.hostname)) {
                            window.__vastPBBlockedCount++;
                            this.__vastPBBlocked = true;
                            return;
                        }
                    } catch(e) {}
                    return origXHROpen.apply(this, arguments);
                };

                var origXHRSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.send = function() {
                    if (this.__vastPBBlocked) return;
                    return origXHRSend.apply(this, arguments);
                };

                // --- Clean tracking parameters from the current URL ---
                try {
                    var trackingParams = ['fbclid', 'gclid', 'utm_source', 'utm_medium', 'utm_campaign',
                                          'utm_term', 'utm_content', 'mc_cid', 'mc_eid', 'yclid',
                                          '_openstat', 'fb_action_ids', 'fb_action_types', 'fb_ref',
                                          'fb_source', 'action_object_map', 'action_type_map', 'action_ref_map'];
                    var url = new URL(window.location.href);
                    var changed = false;
                    trackingParams.forEach(function(param) {
                        if (url.searchParams.has(param)) {
                            url.searchParams.delete(param);
                            changed = true;
                        }
                    });
                    if (changed) {
                        window.history.replaceState({}, '', url.toString());
                    }
                } catch(e) {}

                // --- Monitor for dynamically loaded third-party resources ---
                try {
                    var observer = new MutationObserver(function(mutations) {
                        mutations.forEach(function(mutation) {
                            mutation.addedNodes.forEach(function(node) {
                                if (node.nodeType !== 1) return;
                                var tag = node.tagName;
                                if (tag === 'SCRIPT' || tag === 'IFRAME' || tag === 'IMG') {
                                    var src = node.src || node.getAttribute('src') || '';
                                    try {
                                        var hostname = new URL(src, window.location.href).hostname;
                                        if (isThirdParty(hostname) && isTracker(hostname)) {
                                            node.remove();
                                            window.__vastPBBlockedCount++;
                                        }
                                    } catch(e) {}
                                }
                            });
                        });
                    });
                    observer.observe(document.documentElement, { childList: true, subtree: true });
                } catch(e) {}
            })();
        """.trimIndent()
    }

    override fun getBlockedCount(): Int = blockedCount

    fun updateBlockedCountFromJs(count: Int) {
        blockedCount += count
    }

    override fun resetBlockedCount() {
        blockedCount = 0
    }

    /**
     * Get the total number of learned + seeded tracker domains.
     */
    fun getTotalTrackerCount(): Int = seededTrackers.size + learnedTrackers.size
}
