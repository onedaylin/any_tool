package com.onedaylin.anytool.tools;

/**
 * @Author: onedaylin@outlook.com
 * @Date: 2019/8/29 11:03
 * Twitter_Snowflake<br>
 * SnowFlake的结构如下(每部分用-分开):<br>
 * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000 <br>
 * 1位标识，由于long基本类型在Java中是带符号的，最高位是符号位，正数是0，负数是1，所以id一般是正数，最高位是0<br>
 * 41位时间截(毫秒级)，注意，41位时间截不是存储当前时间的时间截，而是存储时间截的差值（当前时间截 - 开始时间截)
 * 得到的值），这里的的开始时间截，一般是我们的id生成器开始使用的时间，由我们程序来指定的（如下下面程序IdWorker类的startTime属性）。41位的时间截，可以使用69年，年T = (1L << 41) / (1000L * 60 * 60 * 24 * 365) = 69<br>
 * 10位的数据机器位，可以部署在1024个节点，包括5位datacenterId和5位workerId<br>
 * 12位序列，毫秒内的计数，12位的计数顺序号支持每个节点每毫秒(同一机器，同一时间截)产生4096个ID序号<br>
 * 加起来刚好64位，为一个Long型。<br>
 * SnowFlake的优点是，整体上按照时间自增排序，并且整个分布式系统内不会产生ID碰撞(由数据中心ID和机器ID作区分)，并且效率较高，经测试，SnowFlake每秒能够产生26万ID左右。
 * 该实现参考自博客：https://mp.weixin.qq.com/s/1uHZFRImq3u_eFsJNgfYzw
 */
public class SnowflakeDistributeId {


    private final long twepoch = 1546272000L;

    /**
     * 序列id占的位数
     */
    private final long sequenceBits = 12L;

    /**
     * 生成序列的掩码，这里为4095 (0b111111111111=0xfff=4095)，最大序号，用于每次生成序号（+1操作）后相与防止超出
     */
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    /**
     * 机器id所占的位数
     */
    private final long workerIdBits = 5L;

    /**
     * 支持的最大机器id，结果是31 (这个移位算法可以很快的计算出几位二进制数所能表示的最大十进制数)
     */
    private final long workerIdMask = -1L ^ (-1L << workerIdBits);

    /**
     * 机器ID向左移位数（12位）
     */
    private final long workerIdShift = sequenceBits;

    /**
     * 数据标识id所占的位数
     */
    private final long datacenterIdBits = 5L;

    /**
     * 支持的最大数据标识id，结果是31
     */
    private final long datacenterIdMask = -1L ^ (-1L << datacenterIdBits);

    /**
     * 数据标识id向左移位数(12+5)
     */
    private final long datacenterIdShift = sequenceBits + workerIdBits;

    /**
     * 时间截向左移位数(5+5+12)
     */
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;

    /**
     * 工作机器ID（0~31）
     */
    private long workerId;

    /**
     * 数据中心ID(0~31)
     */
    private long datacenterId;

    /**
     * 毫秒内序列(0~4095)
     */
    private long sequence = 0L;

    /**
     * 上次生成ID的时间截
     */
    private long lastTimestamp = -1L;

    /**
     * 时钟回拨时慢了实际时钟的毫秒数
     */
    private long slowGap = 0L;

    public SnowflakeDistributeId(long workerId, long datacenterId) {
        if (workerId > workerIdMask || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker ID can`t be greater then %d or less 0", workerIdMask));
        }
        if (datacenterId > datacenterIdMask || workerId < 0) {
            throw new IllegalArgumentException(String.format("datacenter ID can`t be greater then %d or less 0", datacenterIdMask));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 获得下一个ID (该方法是线程安全的)
     *
     * @return SnowflakeId
     */
    public synchronized long nextId() {

        //当前时间
        long timestamp = genTime();
        //当前时间小于上次生成id时的时间毫秒，说明系统时钟出现回拨情况
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(String.format("system clock moved backwards.Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }
        //如果是同一时间生成的，则进行毫秒内序列
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & sequenceMask;
            //毫秒内序列溢出
            if (sequence == 0) {
                if (timestamp == lastTimestamp) {
                    //阻塞到下一个毫秒,获得新的时间戳
                    timestamp = tilNextMillis();
                }
            }
        } else {
            //时间戳改变，毫秒序列重置
            sequence = 0L;
        }
        //更新上次生成ID的时间截
        lastTimestamp = timestamp;
        return ((lastTimestamp - twepoch) << timestampLeftShift) |
                (datacenterId << datacenterIdShift) |
                (workerId << workerIdShift) |
                sequence;
    }

    /**
     * 阻塞到下一个毫秒，直到获得新的时间戳
     *
     * @return
     */
    protected long tilNextMillis() {
        long currence = genTime();
        //循环知道获取下个毫秒
        while (currence <= lastTimestamp) {
            currence = genTime();
        }
        return currence;
    }

    /**
     * 返回以毫秒为单位的当前时间
     *
     * @return 当前时间（毫秒）
     */
    protected long genTime() {
        return System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (null == o) {
            return false;
        } else if (o instanceof SnowflakeDistributeId) {
            return workerId == ((SnowflakeDistributeId) o).workerId && datacenterId == ((SnowflakeDistributeId) o).datacenterId;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }


    public static void main(String[] args) {
        SnowflakeDistributeId idWorker = new SnowflakeDistributeId(0, 0);
        for (int i = 0; i < 10000; i++) {
            long id = idWorker.nextId();
            System.out.println(id);
        }
    }
}
