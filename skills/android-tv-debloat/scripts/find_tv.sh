#!/usr/bin/env bash
# Find Android TVs on the local network: mDNS services (the instance names often reveal the platform, e.g. a
# Cast name "BeyondTV-..." is TCL's Android TV), the ARP table, and which hosts answer on ADB's port 5555.
# Usage: find_tv.sh            (no changes are made to anything)
set -uo pipefail

browse() {  # browse <service type>: 4 seconds of mDNS results, macOS dns-sd or Linux avahi-browse
  if command -v dns-sd >/dev/null; then
    dns-sd -B "$1" local. > /tmp/find_tv.$$ 2>/dev/null & local pid=$!
    sleep 4; kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
    grep -E ' Add ' /tmp/find_tv.$$ | sed -E 's/.* local\. +[^ ]+ +//' | sort -u | sed "s/^/  $1  /"
    rm -f /tmp/find_tv.$$
  elif command -v avahi-browse >/dev/null; then
    avahi-browse -rpt "$1" 2>/dev/null | awk -F';' '$1=="=" {print "  '"$1"'  " $4 "  " $8}' | sort -u
  fi
}

resolve() {  # resolve <instance> <type>: host name and TXT record (macOS)
  command -v dns-sd >/dev/null || return
  dns-sd -L "$1" "$2" local. > /tmp/find_tv_r.$$ 2>/dev/null & local pid=$!
  sleep 3; kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
  grep -E 'can be reached|bt=|id=' /tmp/find_tv_r.$$ | sed 's/^/      /'
  rm -f /tmp/find_tv_r.$$
}

echo "== mDNS (Android TV remote, Cast, Wireless debugging)"
for t in _androidtvremote2._tcp _googlecast._tcp _adb-tls-connect._tcp; do browse "$t"; done
if command -v dns-sd >/dev/null; then
  for name in $(browse _androidtvremote2._tcp | awk '{print $2}'); do
    echo "  resolving $name:"; resolve "$name" _androidtvremote2._tcp
  done
fi

echo "== Hosts on this network answering on ADB port 5555"
SUBNET=$( (ipconfig getifaddr en0 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}') | cut -d. -f1-3)
if [ -n "$SUBNET" ]; then
  for i in $(seq 1 254); do ping -c 1 -W 1 "$SUBNET.$i" >/dev/null 2>&1 & done; wait
fi
arp -an 2>/dev/null | grep -v incomplete | grep -oE '\(([0-9]+\.){3}[0-9]+\) at [0-9a-f:]+' | tr -d '()' | grep -vE '^(22[4-9]|23[0-9])\.' | while read -r ip _ mac; do
  if nc -z -G 1 "$ip" 5555 >/dev/null 2>&1 || nc -z -w 1 "$ip" 5555 >/dev/null 2>&1; then open="5555 OPEN"; else open=""; fi
  echo "  $ip  $mac  $open"
done
echo "Match a Bluetooth address from mDNS (bt=..) to an ARP MAC: they usually differ only in the last byte."
