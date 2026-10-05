// Exercise the real window notification handler without starting the application,
// loading sensor drivers, opening the LCD, or suspending Windows.
#define wmain purrlcdMainForTest
#include "app.cpp"
#undef wmain

namespace {
void require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}
void notifyPower(WPARAM event) {
    require(windowProc(nullptr, WM_POWERBROADCAST, event, 0) == TRUE,
            "Power notification was not acknowledged");
}
}

int main() {
    wakeEvent = CreateEventW(nullptr, FALSE, FALSE, nullptr);
    if (!wakeEvent) return 1;
    try {
        state.wanted = state.connected = true;
        state.deviceInfo = "old device session";
        notifyPower(PBT_APMSUSPEND);
        require(state.suspended && !state.connected && state.wanted,
                "Suspend must invalidate the session without losing connection intent");
        require(!snapshotStatus()["connecting"].get<bool>(), "Suspend must not advertise connecting");
        require(state.deviceInfo.empty(), "Old device information survived suspend");
        require(WaitForSingleObject(wakeEvent, 0) == WAIT_OBJECT_0, "Worker was not woken for suspend");
        auto suspendedRevision = state.powerRevision;

        notifyPower(PBT_APMRESUMEAUTOMATIC);
        require(!state.suspended && state.wanted && !state.connected,
                "Wake must request a fresh session, not reuse the old connected flag");
        require(state.powerRevision != suspendedRevision, "Wake did not invalidate worker caches");
        require(snapshotStatus()["connecting"].get<bool>(), "Wake did not advertise reconnection");
        require(WaitForSingleObject(wakeEvent, 0) == WAIT_OBJECT_0, "Worker was not woken for resume");

        auto resumedRevision = state.powerRevision;
        notifyPower(PBT_APMRESUMESUSPEND);
        require(state.powerRevision == resumedRevision, "User-present notification caused a second reset");
        require(WaitForSingleObject(wakeEvent, 0) == WAIT_TIMEOUT, "Duplicate notification woke the worker");

        // A user Stop must survive sleep, including Stop issued while suspended.
        notifyPower(PBT_APMSUSPEND);
        state.wanted = false;
        notifyPower(PBT_APMRESUMEAUTOMATIC);
        require(!state.wanted && !state.connected && !snapshotStatus()["connecting"].get<bool>(),
                "Wake re-enabled a stopped display");

        // Resume can arrive even when the suspend event was not observed.
        state.wanted = state.connected = true;
        auto oldRevision = state.powerRevision;
        notifyPower(PBT_APMRESUMEAUTOMATIC);
        require(state.powerRevision != oldRevision && state.wanted && !state.connected,
                "Resume without suspend must still discard the old session");
        CloseHandle(wakeEvent);
        std::cout << "Power resume regression checks passed\n";
        return 0;
    } catch (const std::exception& e) {
        CloseHandle(wakeEvent);
        std::cerr << e.what() << '\n';
        return 1;
    }
}
