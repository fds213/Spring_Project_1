// 채팅 방 목록·안 읽은 수 실시간 갱신. /chat/updates 웹소켓이 "UNREAD_CHANGED"를 알리면 GET /chat/unread 를 다시 받아
// window 의 "ieum:chat-unread" 이벤트로 나눠 준다(detail = { total, rooms }).
// 채팅 화면은 처음부터 켜 두고, 다른 화면의 채팅 패널은 열려 있는 동안만 켠다(ChatUpdates.start / stop).
// 여러 곳이 같이 켜면 하나라도 켜 둔 동안 연결을 유지한다.
(function () {
    const contextPath = document.body.dataset.ctx || "";
    let socket;
    let retryTimer;
    let users = 0;
    let loading = false;
    let refreshPending = false;
    let last = null;

    function running() { return users > 0; }

    async function refresh() {
        if (!running()) return;
        if (loading) {
            refreshPending = true;
            return;
        }
        loading = true;
        try {
            const response = await fetch(`${contextPath}/chat/unread`, {
                headers: { "X-Requested-With": "XMLHttpRequest" },
                cache: "no-store"
            });
            if (response.status === 401 || response.status === 403) {
                users = 0;
                close();
                return;
            }
            if (!response.ok) return;
            last = await response.json();
            if (running()) {
                // 사이드바·채팅 버튼의 배지도 15초 조회(common.js)를 기다리지 않고 맞춘다
                document.querySelectorAll('[data-badge="chat"]').forEach(function (badge) {
                    badge.textContent = last.total > 99 ? "99+" : String(last.total);
                    badge.hidden = last.total === 0;
                });
                window.dispatchEvent(new CustomEvent("ieum:chat-unread", { detail: last }));
            }
        } catch (error) {
            console.debug("채팅 안 읽은 수 조회 실패", error);
        } finally {
            loading = false;
            if (refreshPending) {
                refreshPending = false;
                refresh();
            }
        }
    }

    function connect() {
        if (!running() || socket) return;
        const scheme = location.protocol === "https:" ? "wss://" : "ws://";
        const connection = new WebSocket(`${scheme}${location.host}${contextPath}/chat/updates`);
        socket = connection;
        connection.onopen = refresh;
        connection.onmessage = function (event) {
            if (JSON.parse(event.data).type === "UNREAD_CHANGED") refresh();
        };
        connection.onclose = function (event) {
            if (socket !== connection) return;
            socket = null;
            if (event.code === 1008) {
                users = 0;
                return;
            }
            if (running()) {
                // 로그인이 끊겨 업그레이드가 거절된 경우에도 조회로 인증 상태를 확인한다.
                refresh();
                retryTimer = setTimeout(connect, 3000);
            }
        };
    }

    function close() {
        clearTimeout(retryTimer);
        const connection = socket;
        socket = null;
        if (connection) connection.close(1000);
    }

    window.ChatUpdates = {
        /** 켠다. 마지막으로 받은 목록이 있으면 바로 한 번 나눠 준다 */
        start() {
            users++;
            if (last) window.dispatchEvent(new CustomEvent("ieum:chat-unread", { detail: last }));
            // 웹소켓이 열리기를 기다리지 않고 목록부터 받는다(열리면 한 번 더 받는다)
            refresh();
            connect();
        },
        /** start 한 곳이 하나씩 끈다. 모두 끄면 연결을 닫는다 */
        stop() {
            users = Math.max(0, users - 1);
            if (!running()) close();
        },
        refresh,
        /** 마지막으로 받은 { total, rooms } (없으면 null) */
        last() { return last; }
    };

    let suspended = 0;
    window.addEventListener("pagehide", function () {
        suspended = users;
        users = 0;
        close();
    });
    window.addEventListener("pageshow", function (event) {
        if (event.persisted && suspended) {
            users = suspended;
            suspended = 0;
            connect();
        }
    });
})();
