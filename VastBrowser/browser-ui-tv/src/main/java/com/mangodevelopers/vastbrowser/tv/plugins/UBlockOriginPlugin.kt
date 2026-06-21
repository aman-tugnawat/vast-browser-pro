/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mangodevelopers.vastbrowser.tv.plugins

import android.content.Context
import android.util.Log
import com.mangodevelopers.vastbrowser.tv.R

/**
 * uBlock Origin–style content blocker.
 *
 * How it works on a WebView (SystemEngine):
 * 1. On initialization, parses bundled EasyList + EasyPrivacy filter files from assets/.
 * 2. Extracts two kinds of rules:
 *    - **Cosmetic filters** (e.g. `##.ad-banner`) → CSS selectors to hide elements.
 *    - **Network filters** (e.g. `||doubleclick.net^`) → Domain patterns to block via JS.
 * 3. On page load, injects a content script that:
 *    - Applies CSS `display:none !important` to all matching cosmetic selectors.
 *    - Overrides `XMLHttpRequest` and `fetch()` to block requests matching network filter patterns.
 *    - Uses a `MutationObserver` to re-apply cosmetic filters as the DOM changes (dynamic ads).
 *    - Reports blocked count back via a JS→Android bridge variable.
 */
class UBlockOriginPlugin : BasePlugin(
    PluginInfo(
        id = "ublock_origin",
        name = "uBlock Origin",
        description = "Efficient ad & tracker blocker. Blocks ads, pop-ups, and tracking scripts using EasyList and EasyPrivacy filter lists.",
        version = "1.0.0",
        iconRes = R.drawable.ic_ublock,
        defaultEnabled = true
    )
) {
    companion object {
        private const val TAG = "UBlockOriginPlugin"
        private const val MAX_COSMETIC_RULES = 5000
        private const val MAX_NETWORK_RULES = 5000
    }

    // Parsed cosmetic filter CSS selectors (e.g. ".ad-banner", "#google_ads_frame")
    private val cosmeticSelectors = mutableListOf<String>()

    // Parsed network filter domain patterns (e.g. "doubleclick.net", "googlesyndication.com")
    private val blockedDomains = mutableListOf<String>()

    @Volatile
    private var blockedCount = 0

    @Volatile
    private var initialized = false

    override fun initialize(context: Context) {
        if (initialized) return
        try {
            parseFilterList(context, "filters/easylist_slim.txt")
            parseFilterList(context, "filters/easyprivacy_slim.txt")
            initialized = true
            Log.d(TAG, "Initialized: ${cosmeticSelectors.size} cosmetic rules, ${blockedDomains.size} network rules")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize filter lists", e)
        }
    }

    /**
     * Parse an Adblock Plus–format filter list file from assets.
     * Extracts cosmetic filters (## selectors) and network filters (|| domain patterns).
     */
    private fun parseFilterList(context: Context, assetPath: String) {
        try {
            context.assets.open(assetPath).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()

                    // Skip comments, empty lines, and header
                    if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) continue

                    // Cosmetic filter: "##.selector" or "###id"
                    val cosmeticIdx = trimmed.indexOf("##")
                    if (cosmeticIdx >= 0 && !trimmed.contains("#@#")) {
                        val selector = trimmed.substring(cosmeticIdx + 2).trim()
                        if (selector.isNotEmpty() && cosmeticSelectors.size < MAX_COSMETIC_RULES) {
                            // Only take generic cosmetic filters (no domain prefix) for simplicity
                            if (cosmeticIdx == 0) {
                                cosmeticSelectors.add(selector)
                            }
                        }
                        continue
                    }

                    // Network filter: "||domain.com^" — extract domain
                    if (trimmed.startsWith("||") && !trimmed.startsWith("||/")) {
                        val domainEnd = trimmed.indexOfAny(charArrayOf('^', '/', '$', '*'), startIndex = 2)
                        val domain = if (domainEnd > 2) {
                            trimmed.substring(2, domainEnd)
                        } else {
                            trimmed.substring(2)
                        }
                        if (domain.contains(".") && !domain.contains("*") && blockedDomains.size < MAX_NETWORK_RULES) {
                            blockedDomains.add(domain.lowercase())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse filter list: $assetPath", e)
        }
    }

    override fun generateInjectionScript(context: Context, url: String): String {
        if (!initialized) initialize(context)

        // Skip injection on internal pages
        if (url.startsWith("about:") || url.startsWith("data:")) return ""

        // Build CSS selector string for cosmetic filtering
        val cssSelectors = cosmeticSelectors.joinToString(",\n") { selector ->
            selector.replace("\\", "\\\\").replace("'", "\\'")
        }

        // Build domain block list as a JS Set for O(1) lookup
        val domainList = blockedDomains.joinToString(",") { "'${it.replace("'", "\\'")}'" }

        return """
            (function() {
                if (window.__vastUblockInjected) return;
                window.__vastUblockInjected = true;
                window.__vastUblockCount = 0;

                // --- Cosmetic Filtering: hide ad elements via CSS ---
                try {
                    var style = document.createElement('style');
                    style.id = 'vast-ublock-cosmetic';
                    style.textContent = '$cssSelectors { display: none !important; visibility: hidden !important; height: 0 !important; overflow: hidden !important; }';
                    (document.head || document.documentElement).appendChild(style);

                    // Count initially hidden elements
                    try {
                        var hiddenEls = document.querySelectorAll('$cssSelectors');
                        window.__vastUblockCount += hiddenEls.length;
                    } catch(e) {}
                } catch(e) {}

                // --- Dynamic cosmetic filtering via MutationObserver ---
                try {
                    var observer = new MutationObserver(function(mutations) {
                        try {
                            var newHidden = document.querySelectorAll('$cssSelectors');
                            // Re-count (simpler than diffing)
                        } catch(e) {}
                    });
                    observer.observe(document.documentElement, { childList: true, subtree: true });
                } catch(e) {}

                // --- Network filtering: block requests to known ad/tracker domains ---
                var blockedDomains = new Set([$domainList]);

                function isDomainBlocked(url) {
                    try {
                        var hostname = new URL(url).hostname.toLowerCase();
                        if (blockedDomains.has(hostname)) return true;
                        // Check parent domains (e.g. sub.doubleclick.net → doubleclick.net)
                        var parts = hostname.split('.');
                        for (var i = 1; i < parts.length - 1; i++) {
                            var parent = parts.slice(i).join('.');
                            if (blockedDomains.has(parent)) return true;
                        }
                    } catch(e) {}
                    return false;
                }

                // Override XMLHttpRequest
                var origXHROpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    try {
                        var fullUrl = new URL(url, window.location.href).href;
                        if (isDomainBlocked(fullUrl)) {
                            window.__vastUblockCount++;
                            this.__vastBlocked = true;
                            return;
                        }
                    } catch(e) {}
                    return origXHROpen.apply(this, arguments);
                };

                var origXHRSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.send = function() {
                    if (this.__vastBlocked) return;
                    return origXHRSend.apply(this, arguments);
                };

                // Override fetch
                var origFetch = window.fetch;
                window.fetch = function(input, init) {
                    try {
                        var url = (typeof input === 'string') ? input : (input.url || '');
                        var fullUrl = new URL(url, window.location.href).href;
                        if (isDomainBlocked(fullUrl)) {
                            window.__vastUblockCount++;
                            return Promise.reject(new TypeError('Blocked by uBlock Origin'));
                        }
                    } catch(e) {}
                    return origFetch.apply(this, arguments);
                };

                // Override createElement to block script/iframe/img loading from blocked domains
                var origCreateElement = document.createElement.bind(document);
                document.createElement = function(tag) {
                    var el = origCreateElement(tag);
                    var tagLower = tag.toLowerCase();
                    if (tagLower === 'script' || tagLower === 'iframe' || tagLower === 'img') {
                        var origSetAttr = el.setAttribute.bind(el);
                        el.setAttribute = function(name, value) {
                            if ((name === 'src' || name === 'href') && isDomainBlocked(value)) {
                                window.__vastUblockCount++;
                                return;
                            }
                            return origSetAttr(name, value);
                        };

                        // Also intercept property setter for 'src'
                        var descriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'src') ||
                                          Object.getOwnPropertyDescriptor(el.__proto__, 'src');
                        if (descriptor && descriptor.set) {
                            Object.defineProperty(el, 'src', {
                                set: function(val) {
                                    try {
                                        if (isDomainBlocked(new URL(val, window.location.href).href)) {
                                            window.__vastUblockCount++;
                                            return;
                                        }
                                    } catch(e) {}
                                    descriptor.set.call(this, val);
                                },
                                get: descriptor.get ? function() { return descriptor.get.call(this); } : undefined,
                                configurable: true
                            });
                        }
                    }
                    return el;
                };
            })();
        """.trimIndent()
    }

    override fun getBlockedCount(): Int = blockedCount

    /**
     * Retrieve the blocked count from the WebView's JS context and update
     * the native counter. Called by PluginManager after injection.
     */
    fun updateBlockedCountFromJs(count: Int) {
        blockedCount += count
    }

    override fun resetBlockedCount() {
        blockedCount = 0
    }
}
