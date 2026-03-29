import json
import matplotlib.pyplot as plt
import torch
import itertools
from collections import defaultdict

from .utils import pairing


def get_trace_frames_with_loc(frames, loc):
    return list(filter(lambda x: x["location"] == loc, frames))


class Trace:

    def __init__(self, json_file):

        # load json trace
        with open(json_file) as f:
            self.data = json.load(f)

        # set class names and method
        self.class_name = self.data["class"]
        self.method_name = self.data["method"]
        self.traces = self.data["traces"]

    def get_nth_trace(self, n):
        return self.traces[n]

    def get_pos_identifiers(self, trace=0, frame=0):
        for trace in self.traces:
            if len(trace["frames"]) > 0:
                return trace["frames"][0]["names"]
        assert False

    def get_data_of_nth_frame(self, nth):
        trace = self.get_nth_trace(nth)
        frames = trace["frames"]
        # flt_rep = [list(map(lambda x: float(x), frame["rep"])) for frame in frames]
        return frames

    def get_data_for_loc(self, loc):
        return list(map(lambda x: get_trace_frames_with_loc(x["frames"], loc), self.traces))

    def get_loc_matrix_nth_trace(self, nth, loc):
        trace = self.get_nth_trace(nth)
        frames = trace["frames"]
        locs = get_trace_frames_with_loc(frames, loc)
        return list(map(lambda x: x["rep"], locs))

    def get_all_matrices_for_loc(self, loc):
        res = []
        for x in range(0, self.number_of_traces()):
            res.append(self.get_loc_matrix_nth_trace(x, loc))

        return res

    def plot_nth_trace(self, n, exclude=None):
        if exclude is None:
            exclude = []
        trace = self.get_nth_trace(n)
        frames = trace["frames"]
        flt_rep = [frame["rep"] for frame in frames]

        # plot
        fig, ax = plt.subplots()
        for i, name in enumerate(frames[5]["names"]):
            if name in exclude:
                continue
            vals = [x[i] for x in flt_rep]
            ax.plot(vals, label=name)
        ax.legend()
        plt.plot()
        plt.show()

    def number_of_traces(self):
        return len(self.traces)

    def get_pairing_of_nth_trace(self, nth, loc):
        mat = self.get_loc_matrix_nth_trace(nth, loc)
        return pairing(mat)

    def pair_nth_trace_multi(self, nth, loc, locs):
        before, after = [], []

        outers = locs[:locs.index(loc)]

        trace = self.get_nth_trace(nth)
        frames = trace["frames"]

        tmp = []
        for i, frame in enumerate(frames):
            if frame["location"] in outers:
                b, a = pairing(tmp)
                before += b
                after += a
                tmp = []
                continue
            if frame["location"] == loc:
                tmp.append(frame["rep"])
            if i == len(frames) - 1:
                b, a = pairing(tmp)
                before += b
                after += a

        return before, after

    def pair_all_traces_multi(self, loc, locs):
        before, after = [], []
        for trc in range(0, self.number_of_traces()):
            x, y = self.pair_nth_trace_multi(trc, loc, locs)
            before += x
            after += y

        return before, after

    def pair_all_traces_multi_as_tensor(self, loc, locs):
        before, after = self.pair_all_traces_multi(loc, locs)
        input_before = list(map(lambda x: torch.Tensor(x), before))
        input_after = list(map(lambda x: torch.Tensor(x), after))

        input_before = torch.stack(input_before)
        input_after = torch.stack(input_after)

        return input_before, input_after

    def get_pairing_of_all_traces(self, loc):
        tuples = map(lambda x: self.get_pairing_of_nth_trace(x, loc), range(0, self.number_of_traces()))
        before, after = [], []
        for x, y in tuples:
            before += x
            after += y

        return before, after

    def get_pairing_of_all_traces_as_tensors(self, loc):
        before, after = self.get_pairing_of_all_traces(loc)
        input_before = list(map(lambda x: torch.Tensor(x), before))
        input_after = list(map(lambda x: torch.Tensor(x), after))

        input_before = torch.stack(input_before)
        input_after = torch.stack(input_after)

        return input_before, input_after

    def get_wrangled_traces_as_tensors(self, loc):
        min_trace_len = 10
        start_cutoff = 5
        mat = self.get_all_matrices_for_loc(loc)
        # remove (very short) short traces
        mat = filter(lambda x: len(x) >= min_trace_len, mat)
        # remove initialization phase and pair
        mat = map(lambda x: x[start_cutoff:], mat)
        before, after = [], []
        for x in mat:
            b, a = pairing(x)
            before += b
            after += a

        input_before = list(map(lambda x: torch.Tensor(x), before))
        input_after = list(map(lambda x: torch.Tensor(x), after))
        input_before = torch.stack(input_before)
        input_after = torch.stack(input_after)

        return input_before, input_after

    def lexicographic_pairing_as_tensors(self, locs):

        res_dict = defaultdict(list)

        for n_trace in range(0, self.number_of_traces()):
            trace = self.get_nth_trace(n_trace)
            frames = trace["frames"]
            for window_i in range(0, len(frames) - 1):
                before = frames[window_i]
                after = frames[window_i + 1]

                transition = tuple(sorted([before["location"], after["location"]]))
                res_dict[transition].append((before["rep"], after["rep"]))

        res = {}

        for k, lst in res_dict.items():
            before = []
            after = []
            for bef, af in lst:
                before.append(torch.Tensor(bef))
                after.append(torch.Tensor(af))

            before = torch.stack(before)
            after = torch.stack(after)

            res[k] = (before, after)

        return res


if __name__ == '__main__':
    t = Trace('../GenTraces/test.json')
    t.plot_nth_trace(2, "x")
    print(t.get_data_of_nth_frame(1))
    print(t.get_data_for_loc(29))
    print(t.get_loc_matrix_nth_trace(1, 29))
    for x in t.get_all_matrices_for_loc(29):
        print(x)
