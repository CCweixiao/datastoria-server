import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";

export const nodeZkMetricsDashboard: TimeseriesDescriptor[] = [
  // ProfileEvent_ZooKeeperBytesReceived
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Bytes Received",
      descriptionKey: "monitor.cluster.zookeeperBytesReceived.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperBytesReceived)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperBytesSent
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Bytes Sent",
      descriptionKey: "monitor.cluster.zookeeperBytesSent.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperBytesSent)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperCheck
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Check",
      descriptionKey: "monitor.cluster.zookeeperCheck.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperCheck)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperClose
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Close",
      descriptionKey: "monitor.cluster.zookeeperClose.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperClose)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperCreate
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Create",
      descriptionKey: "monitor.cluster.zookeeperCreate.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperCreate)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperExists
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Exists",
      descriptionKey: "monitor.cluster.zookeeperExists.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperExists)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperGet
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Get",
      descriptionKey: "monitor.cluster.zookeeperGet.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperGet)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperHardwareExceptions
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Hardware Exceptions",
      descriptionKey: "monitor.cluster.zookeeperHardwareExceptions.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperHardwareExceptions)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperInit
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Init",
      descriptionKey: "monitor.cluster.zookeeperInit.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperInit)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperList
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper List",
      descriptionKey: "monitor.cluster.zookeeperList.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperList)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperMulti
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Multi",
      descriptionKey: "monitor.cluster.zookeeperMulti.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperMulti)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperMultiRead
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Multi Read",
      descriptionKey: "monitor.cluster.zookeeperMultiRead.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperMultiRead)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperMultiWrite
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Multi Write",
      descriptionKey: "monitor.cluster.zookeeperMultiWrite.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
    sum(ProfileEvent_ZooKeeperMultiWrite)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperOtherExceptions
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Other Exceptions",
      descriptionKey: "monitor.cluster.zookeeperOtherExceptions.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperOtherExceptions)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperReconfig
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Reconfig",
      descriptionKey: "monitor.cluster.zookeeperReconfig.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperReconfig)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperRemove
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Remove",
      descriptionKey: "monitor.cluster.zookeeperRemove.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperRemove)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperSet
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Set",
      descriptionKey: "monitor.cluster.zookeeperSet.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperSet)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperSync
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Sync",
      descriptionKey: "monitor.cluster.zookeeperSync.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperSync)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperTransactions
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Transactions",
      descriptionKey: "monitor.cluster.zookeeperTransactions.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperTransactions)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperUserExceptions
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper User Exceptions",
      descriptionKey: "monitor.cluster.zookeeperUserExceptions.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperUserExceptions)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperWaitMicroseconds
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Wait Microseconds",
      descriptionKey: "monitor.cluster.zookeeperWait.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperWaitMicroseconds)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,

  // ProfileEvent_ZooKeeperWatchResponse
  {
    type: "line",
    titleOption: {
      title: "ZooKeeper Watch Response",
      descriptionKey: "monitor.cluster.zookeeperWatchResponse.description",
      align: "center",
    },
    legendOption: {
      placement: "none",
    },
    gridPos: { w: 6, h: 6 },
    datasource: {
      sql: `
SELECT
  toStartOfInterval(event_time, INTERVAL {rounding:UInt32} SECOND)::INT as t,
  sum(ProfileEvent_ZooKeeperWatchResponse)
FROM system.metric_log
WHERE event_date >= toDate({from:String}) 
  AND event_date <= toDate({to:String})
  AND event_time >= {from:String} 
  AND event_time < {to:String}
GROUP BY t
ORDER BY t WITH FILL STEP {rounding:UInt32}
`,
    },
  } as TimeseriesDescriptor,
];
