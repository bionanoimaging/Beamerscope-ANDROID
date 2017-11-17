/* Copyright 2015 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

#ifndef TENSORFLOW_FRAMEWORK_NODE_DEF_BUILDER_H_
#define TENSORFLOW_FRAMEWORK_NODE_DEF_BUILDER_H_

#include <functional>
#include <vector>
#include "tensorflow/core/framework/attr_value_util.h"
#include "tensorflow/core/framework/graph.pb.h"
#include "tensorflow/core/framework/node_def_util.h"
#include "tensorflow/core/framework/op.h"
#include "tensorflow/core/framework/op_def.pb.h"
#include "tensorflow/core/framework/types.h"
#include "tensorflow/core/lib/core/status.h"
#include "tensorflow/core/lib/gtl/array_slice.h"
#include "tensorflow/core/lib/strings/strcat.h"

namespace tensorflow {

class NodeDefBuilder;
typedef std::function<Status(const OpDef&, int, const NodeDef&,
                             NodeDefBuilder*)>
    FakeInputFunctor;

// This is a helper for creating a NodeDef.  Automatically sets attrs
// that can be inferred from the inputs, and uses default values
// (where they exist) for unspecified attrs.  Example usage:
//
//  NodeDef node_def;
//  Status status = NodeDefBuilder(node_name, op_name)
//                           .Input(...)
//                           .Attr(...)
//                           .Finalize(&node_def);
//  if (!status.ok()) return status;
//  // Use node_def here.
class NodeDefBuilder {
 public:
  // To specify an output to be consumed by one of the Input() methods below.
  struct NodeOut {
    NodeOut(StringPiece n, int i, DataType dt);
    NodeOut();  // uninitialized, call Reset() before use.
    void Reset(StringPiece n, int i, DataType dt);
    string node;
    int index;
    DataType data_type;
  };

  // Specify the name and the Op (either via an OpDef or the name of
  // the Op plus a registry) for the NodeDef.  Other fields are
  // specified by calling the methods below.
  // REQUIRES: The OpDef must satisfy ValidateOpDef().
  NodeDefBuilder(StringPiece name, StringPiece op_name,
                 const OpRegistryInterface* op_registry = OpRegistry::Global());
  // REQUIRES: in addition, *op_def must outlive *this.
  NodeDefBuilder(StringPiece name, const OpDef* op_def);

  // You must call one Input() function per input_arg in the Op,
  // *and in the same order as the input_args appear in the OpDef.*

  // For inputs that take a single tensor.
  NodeDefBuilder& Input(StringPiece src_node, int src_index, DataType dt) {
    const OpDef::ArgDef* arg = NextArgDef();
    if (arg != nullptr) SingleInput(arg, src_node, src_index, dt);
    return *this;
  }
  NodeDefBuilder& Input(const NodeOut& src) {
    Input(src.node, src.index, src.data_type);
    return *this;
  }

  // For inputs that take a list of tensors.
  NodeDefBuilder& Input(gtl::ArraySlice<NodeOut> src_list) {
    const OpDef::ArgDef* arg = NextArgDef();
    if (arg != nullptr) ListInput(arg, src_list);
    return *this;
  }

  // To create inputs in tests, see fake_input.h.
  NodeDefBuilder& Input(FakeInputFunctor fake_input);

  // Specify that this node must only run after src_node.
  NodeDefBuilder& ControlInput(StringPiece src_node);

  // Constrains what devices this node may be scheduled on.
  NodeDefBuilder& Device(StringPiece device_spec);

  // Sets the attr, if not already set.  If already set with a different
  // value, an error will be returned from Finalize().
  template <class T>
  NodeDefBuilder& Attr(StringPiece attr_name, T&& value);
  // Note: overload needed to allow {...} expressions for value.
  template <class T>
  NodeDefBuilder& Attr(StringPiece attr_name, std::initializer_list<T> value) {
    Attr<std::initializer_list<T>>(attr_name, std::move(value));
    return *this;
  }

  // Finish building the NodeDef, returning any errors or setting
  // *node_def if none.
  // WARNING: Not all problems are detected!  The resulting NodeDef may
  // not be valid!  Call ValidateNodeDef() from node_def_utils to be sure.
  Status Finalize(NodeDef* node_def) const;

  // Accessors for the values set in the constructor.
  const string& node_name() const { return node_def_.name(); }
  const OpDef& op_def() const { return *op_def_; }

 private:
  // Called in the constructors.
  void Initialize();

  // Get the current ArgDef and advance to the next one. Returns nullptr
  // if no more inputs are available.
  const OpDef::ArgDef* NextArgDef();

  // Returns true if there is still an input_arg available in *op_def_,
  // otherwise adds to error_ and returns false.
  bool NextArgAvailable();

  // These do the main work of the Input() methods.
  void SingleInput(const OpDef::ArgDef* input_arg, StringPiece src_node,
                   int src_index, DataType dt);
  void ListInput(const OpDef::ArgDef* input_arg,
                 gtl::ArraySlice<NodeOut> src_list);

  // Add "src_node:src_index" to the list of inputs in the node_def_.
  void AddInput(StringPiece src_node, int src_index);

  // Generate an error if you can't pass dt when expected is expected.
  void VerifyInputType(const OpDef::ArgDef* input_arg, DataType expected,
                       DataType dt);

  // If input_arg->is_ref() is true, generate an error if dt is not a ref.
  void VerifyInputRef(const OpDef::ArgDef* input_arg, DataType dt);

  // Makes dt a ref type if that is what the input_arg specifies.
  DataType MaybeAddRef(const OpDef::ArgDef* input_arg, DataType dt) {
    return input_arg->is_ref() ? MakeRefType(dt) : dt;
  }

  void CheckInconsistency(StringPiece attr_name, const AttrValue& found,
                          const AttrValue& attr_value);

  const OpDef* op_def_;
  NodeDef node_def_;
  int inputs_specified_;
  std::vector<string> control_inputs_;
  std::vector<string> errors_;
};

// IMPLEMENTATION -------------------------------------------------------------

template <class T>
NodeDefBuilder& NodeDefBuilder::Attr(StringPiece attr_name, T&& value) {
  const AttrValue* found = AttrSlice(node_def_).Find(attr_name);
  if (found == nullptr) {
    AddNodeAttr(attr_name, std::forward<T>(value), &node_def_);
  } else {
    AttrValue attr_value;
    SetAttrValue(std::forward<T>(value), &attr_value);
    CheckInconsistency(attr_name, *found, attr_value);
  }
  return *this;
}

}  // namespace tensorflow

#endif  // TENSORFLOW_FRAMEWORK_NODE_DEF_BUILDER_H_
