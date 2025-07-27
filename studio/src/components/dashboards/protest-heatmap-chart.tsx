
"use client"

import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"
import { Map } from "lucide-react"

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
} from "@/components/ui/chart"

const chartData = [
  { location: "Freedom Park", protests: 186 },
  { location: "Town Hall", protests: 305 },
  { location: "MG Road", protests: 237 },
  { location: "Majestic", protests: 73 },
  { location: "Koramangala", protests: 209 },
  { location: "Indiranagar", protests: 214 },
]

const chartConfig = {
  protests: {
    label: "Protests",
    color: "hsl(var(--destructive))",
  },
}

export function ProtestHeatmapChart() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Map /> Historical Protest Heatmap</CardTitle>
        <CardDescription>Frequency and location of past protests.</CardDescription>
      </CardHeader>
      <CardContent>
        <ChartContainer config={chartConfig} className="h-[200px] w-full">
          <BarChart accessibilityLayer data={chartData} layout="vertical" margin={{ left: 10 }}>
            <CartesianGrid horizontal={false} />
            <YAxis
              dataKey="location"
              type="category"
              tickLine={false}
              tickMargin={10}
              axisLine={false}
              tickFormatter={(value) => value.slice(0, 12)}
            />
            <XAxis dataKey="protests" type="number" hide />
            <ChartTooltip
              cursor={false}
              content={<ChartTooltipContent indicator="line" />}
            />
            <Bar dataKey="protests" fill="var(--color-protests)" radius={4} />
          </BarChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}
