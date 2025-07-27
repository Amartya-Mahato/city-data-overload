
"use client";

import * as React from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Car, MoreHorizontal } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useToast } from "@/hooks/use-toast";

const hotspots = [
    { id: "HS-01", location: "Koramangala (Silk Board)", level: "Critical", speed: "5 km/h", updated: "2m ago" },
    { id: "HS-02", location: "Marathahalli Bridge", level: "High", speed: "10 km/h", updated: "5m ago" },
    { id: "HS-03", location: "Indiranagar (Tin Factory)", level: "High", speed: "12 km/h", updated: "8m ago" },
    { id: "HS-04", location: "Hebbal Flyover", level: "Medium", speed: "20 km/h", updated: "10m ago" },
];

export default function CongestionHotspotsPage() {
  const { toast } = useToast();

  const handleAction = (action: string, location: string) => {
    toast({
      title: "Action Initiated",
      description: `${action} for hotspot at ${location}.`,
    });
  };

  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Congestion Hotspots</h2>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Car /> Live Congestion Hotspots</CardTitle>
          <CardDescription>Real-time data on traffic congestion across the city.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Hotspot ID</TableHead>
                <TableHead>Location</TableHead>
                <TableHead>Congestion Level</TableHead>
                <TableHead>Avg. Speed</TableHead>
                <TableHead>Last Updated</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {hotspots.map((hotspot) => (
                <TableRow key={hotspot.id}>
                  <TableCell className="font-medium">{hotspot.id}</TableCell>
                  <TableCell>{hotspot.location}</TableCell>
                  <TableCell>
                    <Badge variant={hotspot.level === 'Critical' || hotspot.level === 'High' ? 'destructive' : 'default'}>
                        {hotspot.level}
                    </Badge>
                  </TableCell>
                  <TableCell>{hotspot.speed}</TableCell>
                  <TableCell>{hotspot.updated}</TableCell>
                  <TableCell className="text-right">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" className="h-8 w-8 p-0">
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => handleAction("Viewing on Map", hotspot.location)}>View on Map</DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleAction("Reroute Suggestion Sent", hotspot.location)}>Suggest Reroute</DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleAction("Viewing Camera Feed", hotspot.location)}>View Camera Feed</DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
