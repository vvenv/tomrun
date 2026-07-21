package com.vvenv.tomrun

/**
 * 跑酷内文字提示的统一调度器。
 *
 * 连击、横幅、新纪录原本各画各的，同一时刻触发就会叠在一起糊成一团。
 * 这里把它们收成一条通知带：同时最多显示 [MAX_ROWS] 行，按行号自上而下
 * 错开；放不下的排进队列，等有空行再补显。
 *
 * 行号一旦分配就不再变动——上面的提示先消失也不会让下面的往上跳。
 *
 * 线程：[push] / [tick] / [clear] 由游戏线程与 UI 线程共同调用，故加锁；
 * 绘制端只读 [rows] 这份不可变快照。
 */
class Notices {

    /** 提示样式：绘制端据此取字号与出场动画，行高固定不受影响。 */
    enum class Style { BANNER, COMBO, RECORD }

    class Notice(
        val text: String,
        val color: Int,
        val life: Float,
        val style: Style,
        val priority: Int,
        /** 同 key 的提示互相覆盖（如连击只保留最新一条），不额外占行。 */
        val key: String?
    ) {
        @Volatile var remain = life
        /** 已显示时长，绘制端用来做弹出动画。 */
        val age: Float get() = life - remain
    }

    /** 行槽：下标即屏幕行号，null 表示该行空闲。 */
    private val slots = arrayOfNulls<Notice>(MAX_ROWS)
    /** 等待补位的提示，按优先级降序、同级先来先到。 */
    private val pending = ArrayList<Notice>()

    /** 绘制端只读快照，长度恒为 [MAX_ROWS]。 */
    @Volatile var rows: Array<Notice?> = arrayOfNulls(MAX_ROWS)
        private set

    @Synchronized fun clear() {
        java.util.Arrays.fill(slots, null)
        pending.clear()
        publish()
    }

    /** 加入一条提示：有空行立刻显示，否则排队等待。 */
    @Synchronized fun push(
        text: String,
        color: Int,
        life: Float,
        style: Style = Style.BANNER,
        priority: Int = P_BANNER,
        key: String? = null
    ) {
        if (text.isEmpty() || life <= 0f) return
        val n = Notice(text, color, life, style, priority, key)

        // 同 key 覆盖：连击连着涨时原地刷新，不占新行也不排队
        if (key != null && replaceByKey(n)) {
            publish()
            return
        }

        val free = slots.indexOfFirst { it == null }
        if (free >= 0) {
            slots[free] = n
            publish()
            return
        }

        // 行满：高优先级（新纪录）催促最不重要的一行提前淡出，好尽快腾位
        if (priority >= P_RECORD) hurryLowest(priority)
        insertPending(n)
    }

    /** 每帧推进：扣寿命、回收过期行、按优先级补位。 */
    @Synchronized fun tick(dt: Float) {
        var changed = false
        for (i in slots.indices) {
            val n = slots[i] ?: continue
            n.remain -= dt
            if (n.remain <= 0f) {
                slots[i] = null
                changed = true
            }
        }
        for (i in slots.indices) {
            if (pending.isEmpty()) break
            if (slots[i] != null) continue
            slots[i] = pending.removeAt(0)
            changed = true
        }
        if (changed) publish()
    }

    /** 找到同 key 的行或队列项就地替换；返回是否已处理。 */
    private fun replaceByKey(n: Notice): Boolean {
        for (i in slots.indices) {
            if (slots[i]?.key == n.key) {
                slots[i] = n
                return true
            }
        }
        for (i in pending.indices) {
            if (pending[i].key == n.key) {
                pending[i] = n
                return true
            }
        }
        return false
    }

    /** 按优先级降序插入，同级排在后面（先来先显）。 */
    private fun insertPending(n: Notice) {
        var at = pending.size
        for (i in pending.indices) {
            if (pending[i].priority < n.priority) {
                at = i
                break
            }
        }
        pending.add(at, n)
        // 溢出时丢掉最不重要、最晚到的那条，避免队列越积越长导致提示严重滞后
        while (pending.size > MAX_PENDING) pending.removeAt(pending.size - 1)
    }

    /** 把当前最不重要的一行压到快速淡出，给高优先级提示让路。 */
    private fun hurryLowest(incoming: Int) {
        var target = -1
        for (i in slots.indices) {
            val n = slots[i] ?: continue
            if (n.priority >= incoming) continue
            val cur = slots.getOrNull(target)
            if (cur == null || n.priority < cur.priority ||
                (n.priority == cur.priority && n.remain > cur.remain)
            ) target = i
        }
        val victim = slots.getOrNull(target) ?: return
        if (victim.remain > HURRY_OUT) victim.remain = HURRY_OUT
    }

    private fun publish() {
        rows = slots.copyOf()
    }

    companion object {
        /** 同屏最多几行提示；再多就排队，免得糊住赛道视线。 */
        const val MAX_ROWS = 3
        private const val MAX_PENDING = 8
        /** 被高优先级挤掉时的剩余淡出时长。 */
        private const val HURRY_OUT = 0.3f

        const val P_BANNER = 1
        const val P_COMBO = 2
        const val P_RECORD = 3

        const val KEY_COMBO = "combo"
        const val KEY_RECORD = "record"
    }
}
