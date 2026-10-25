/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2021 Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
package org.cloudsimplus.testbeds.sla.taskcompletiontime;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.resources.Processor;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.allocators.ResourceAllocatorSimple;
import org.cloudsimplus.resources.CoreCapacity;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.testbeds.Experiment;
import org.cloudsimplus.testbeds.ExperimentRunner;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.comparingDouble;
import static org.cloudsimplus.testbeds.sla.taskcompletiontime.CloudletTaskCompletionTimeWorkLoadWithoutMinimizationRunner.VMS;

/// @param <T> the type of the subclass extending this class
abstract class AbstractCloudletTaskCompletionTimeExperiment<T extends Experiment<T>> extends Experiment<T> {
    private static final int HOSTS = 50;
    private static final int HOST_CORES = 32;

    protected AbstractCloudletTaskCompletionTimeExperiment(final int index, @Nullable final ExperimentRunner runner, final long seed) {
        super(index, runner, seed);
        setVmsByBrokerFunction(broker -> VMS);
        setHostsNumber(HOSTS);
    }

    protected DatacenterBroker getFirstBroker() {
        return getBrokerList().stream().findFirst().orElse(DatacenterBroker.NULL);
    }

    protected final void printBrokerFinishedCloudlets(final DatacenterBroker broker) {
        final List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        final Comparator<Cloudlet> sortByVmId = comparingDouble(c -> c.getVm().getId());
        final Comparator<Cloudlet> sortByStartTime = comparingDouble(Cloudlet::getStartTime);
        finishedCloudlets.sort(sortByVmId.thenComparing(sortByStartTime));

        new CloudletsTableBuilder(finishedCloudlets).build();
    }

    /// Gets the total number of vPEs (VM PEs) across all existing VMs.
    /// @return
    protected final double getSumPesVms() {
        return getVmList().stream()
            .mapToDouble(Processor::cores)
            .sum();
    }

    protected final double getSumPesCloudlets() {
        return getCloudletList().stream()
            .mapToDouble(Cloudlet::getRequiredCores)
            .sum();
    }

    /// Shows the average wait time of all cloudlets
    ///
    /// @param cloudletList list of cloudlets
    protected final void waitTimeAverage(final List<Cloudlet> cloudletList) {
        final double averageWaitTime = cloudletList.stream().mapToDouble(Cloudlet::getCreationWaitTime).average().orElse(0);
        System.out.printf("%n# The wait time is: %f%n", averageWaitTime);
    }

    /// Computes the Task Completion Time average for all finished Cloudlets on this
    /// experiment.
    ///
    /// @return the Task Completion Time average or [Double#NaN] if no Cloudlet has finished
    protected double getTaskCompletionTimeAverage() {
        final DatacenterBroker broker = getBrokerList().stream()
            .findFirst()
            .orElse(DatacenterBroker.NULL);

        return broker.getCloudletFinishedList().stream()
            .mapToDouble(c -> c.getFinishTime() - c.getDcArrivalTime())
            .average()
            .orElse(Double.NaN);
    }

    protected final Host createHost(final int id) {

        final var cpuCapacity = new CoreCapacity(HOST_CORES, 100000);

        final Host host = new HostSimple(40960, 10000000, 10000000, cpuCapacity)
            .setRamAllocator(new ResourceAllocatorSimple())
            .setBwAllocator(new ResourceAllocatorSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
        host.setId(id);
        return host;
    }

    @Override
    protected final DatacenterBroker createBroker() {
        return new DatacenterBrokerSimple(getSimulation());
    }

}
