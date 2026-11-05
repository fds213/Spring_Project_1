(function () {
    const contextPath = document.currentScript.dataset.contextPath;
    let socket;
    let retryTimer;
    let stopped = false;
    let loading = false;
    let refreshPending = false;

    async function refresh() {
        if (stopped) return;
        if (loading) {
            refreshPending = true;
            return;
        }
        loading = true;
        try {
            const response = await fetch(contextPath + "/chat/unread", {
                headers: { "X-Requested-With": "XMLHttpRequest" },
                cache: "no-store"
            });
            if (response.status === 401 || response.status === 403) {
                stop();
                return;
            }
            if (!response.ok) return;
            const unread = await response.json();
            if (!stopped) {
                window.dispatchEvent(new CustomEvent("ieum:chat-unread", { detail: unread }));
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
        if (stopped || socket) return;
        const scheme = location.protocol === "https:" ? "wss://" : "ws://";
        const connection = new WebSocket(scheme + location.host + contextPath + "/chat/updates");
        socket = connection;
        connection.onopen = refresh;
        connection.onmessage = function (event) {
            if (JSON.parse(event.data).type === "UNREAD_CHANGED") refresh();
        };
        connection.onclose = function (event) {
            if (socket !== connection) return;
            socket = null;
            if (event.code === 1008) {
                stop();
                return;
            }
            if (!stopped) {
                // 로그인이 끊겨 업그레이드가 거절된 경우에도 조회로 인증 상태를 확인한다.
                refresh();
                retryTimer = setTimeout(connect, 3000);
            }
        };
    }

    function stop() {
        stopped = true;
        clearTimeout(retryTimer);
        const connection = socket;
        socket = null;
        if (connection) connection.close(1000);
    }

    window.addEventListener("pagehide", stop);
    window.addEventListener("pageshow", function (event) {
        if (event.persisted) {
            stopped = false;
            connect();
        }
    });
    connect();
})();
