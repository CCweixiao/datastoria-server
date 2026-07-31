import { DateTimeExtension } from "@/lib/datetime-utils";
import {
  convertToTimestampMs,
  isNumericType,
  isTimestampColumn,
  sampleIsNumeric,
  transformRowsToChartData,
} from "./dashboard-data-utils";

export type ChartAxisValue = number | string;

export interface DetectedChartColumns {
  axisColumn: string;
  isTimeAxis: boolean;
  valueColumns: string[];
  labelColumns: string[];
}

export function detectChartColumns(
  data: Record<string, unknown>[],
  meta: Array<{ name: string; type?: string }>
): DetectedChartColumns {
  if (!data.length) {
    return { axisColumn: "", isTimeAxis: false, valueColumns: [], labelColumns: [] };
  }

  const transformedData = transformRowsToChartData(data, meta);
  const allColumns = meta.length
    ? meta.map((column) => column.name)
    : Object.keys(transformedData[0]);
  let timestampColumn = "";
  const valueColumns: string[] = [];
  const labelColumns: string[] = [];

  allColumns.forEach((column) => {
    const type = meta.find((candidate) => candidate.name === column)?.type;
    if (isTimestampColumn(column, type)) {
      if (!timestampColumn) timestampColumn = column;
    } else if (isNumericType(type) || sampleIsNumeric(transformedData, column)) {
      valueColumns.push(column);
    } else {
      labelColumns.push(column);
    }
  });

  const categoryColumn = timestampColumn ? "" : labelColumns.shift() || "";
  return {
    axisColumn: timestampColumn || categoryColumn,
    isTimeAxis: Boolean(timestampColumn),
    valueColumns,
    labelColumns,
  };
}

export function normalizeChartAxisValue(value: unknown, isTimeAxis: boolean): ChartAxisValue {
  return isTimeAxis ? convertToTimestampMs(value) : String(value ?? "");
}

export function buildChartAxis(
  data: Record<string, unknown>[],
  axisColumn: string,
  isTimeAxis: boolean
): {
  values: ChartAxisValue[];
  labels: string[];
  rowByValue: Map<ChartAxisValue, Record<string, unknown>>;
} {
  const rowByValue = new Map<ChartAxisValue, Record<string, unknown>>();
  data.forEach((row) => {
    rowByValue.set(normalizeChartAxisValue(row[axisColumn], isTimeAxis), row);
  });

  const values = Array.from(rowByValue.keys());
  if (isTimeAxis) {
    values.sort((left, right) => Number(left) - Number(right));
  }

  const labels = values.map((value) => {
    if (!isTimeAxis) return String(value);
    return DateTimeExtension.formatDateTime(new Date(Number(value)), "HH:mm:ss") || "";
  });

  return { values, labels, rowByValue };
}
